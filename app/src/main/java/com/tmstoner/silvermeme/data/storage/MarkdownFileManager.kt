package com.tmstoner.silvermeme.data.storage

import com.tmstoner.silvermeme.data.model.ChecklistItem
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Reads and writes [TodoItem] instances as Obsidian-compatible markdown files.
 *
 * Each file uses YAML frontmatter for structured metadata and an optional markdown
 * body for free-form notes. Example file layout:
 *
 * ```markdown
 * ---
 * id: a1b2c3d4
 * title: Buy groceries
 * status: open
 * priority: high
 * due: 2024-03-15
 * location: Superstore
 * tags:
 *   - shopping
 *   - errands
 * created: 2024-03-01T09:00:00
 * updated: 2024-03-01T09:00:00
 * ---
 *
 * ## Notes
 *
 * Don't forget the reusable bags.
 * ```
 *
 * All TODO files are placed inside a "Tasks/" sub-folder of the vault directory so
 * they can be discovered by Obsidian on desktop.
 */
class MarkdownFileManager(private val vaultDir: File) {

    companion object {
        const val TASKS_FOLDER = "Tasks"
        const val TRASH_FOLDER = "Tasks/.trash"
        /** Items older than this many days are eligible for auto-purge. */
        const val TRASH_RETENTION_DAYS = 30L
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        private const val FRONTMATTER_DELIMITER = "---"

        /**
         * Canonical filename sanitizer. Public so callers (e.g. UI screens) can derive
         * paths that exactly match what [saveTodo] will produce, preventing path mismatches.
         */
        fun sanitizeFilename(title: String): String =
            title
                .replace(Regex("[/\\\\:*?\"<>|#\\p{Cc}]"), "_")
                .trim()
                .trimEnd('.')
                .take(150)
                .ifEmpty { "untitled" }
    }

    private val tasksDir: File get() = File(vaultDir, TASKS_FOLDER)
    private val trashDir: File get() = File(vaultDir, TRASH_FOLDER)

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns all TODO items found in the Tasks folder and subfolders (Track E3). */
    fun getAllTodos(): List<TodoItem> {
        if (!tasksDir.exists()) return emptyList()
        return getAllMarkdownFiles(tasksDir)
            .mapNotNull { parseMarkdownFile(it) }
    }
    
    /** Recursively finds all .md files in a directory and subdirectories (Track E3). */
    private fun getAllMarkdownFiles(dir: File): List<File> {
        val files = mutableListOf<File>()
        dir.listFiles()?.forEach { file ->
            when {
                file.isFile && file.extension == "md" -> files.add(file)
                file.isDirectory -> files.addAll(getAllMarkdownFiles(file))
            }
        }
        return files
    }

    /**
     * Persists [todo] to its backing markdown file.
     * The filename is derived from [TodoItem.title]; if the item was already
     * backed by a different file (title/project changed), the old file is removed.
     * Project folders can be specified by passing a custom filePath (Track E3).
     *
     * @param previousFilePath the file path the item was previously saved under
     *   (before any title/project rename in this save), used to locate and delete
     *   the stale file. Defaults to [TodoItem.filePath] for backward compatibility
     *   when callers don't rename the item.
     */
    fun saveTodo(todo: TodoItem, previousFilePath: String? = null): TodoItem {
        if (!tasksDir.exists()) tasksDir.mkdirs()

        // Always derive the canonical base name from the title, never trust an
        // externally-supplied sanitized form (Bug 1 — filename sanitizer mismatch).
        val sanitizedTitle = sanitizeFilename(todo.title)

        // Determine the parent directory: extract it from filePath if it carries a
        // project sub-path, otherwise default to tasksDir.
        val parentDir: File = if (todo.filePath.isNotBlank() && todo.filePath.contains("/")) {
            // e.g. "Tasks/Work/Report.md" → parent is "<vault>/Tasks/Work"
            File(vaultDir, todo.filePath).parentFile ?: tasksDir
        } else {
            tasksDir
        }
        parentDir.mkdirs()

        // Candidate file using the canonical name.
        val candidateFile = File(parentDir, "$sanitizedTitle.md")

        // Bug 3 — collision avoidance: if the candidate file already exists and
        // belongs to a DIFFERENT id, append the due-date (or current date) suffix
        // so the new occurrence doesn't overwrite the existing one.
        val newFile: File = if (candidateFile.exists() && todo.filePath.isBlank()) {
            val existingId = runCatching {
                parseFrontmatter(candidateFile.readText())["id"]
            }.getOrNull()
            if (existingId != null && existingId != todo.id) {
                val dateSuffix = (todo.dueDate ?: LocalDate.now()).format(DATE_FORMATTER)
                File(parentDir, "$sanitizedTitle-$dateSuffix.md")
            } else {
                candidateFile
            }
        } else {
            candidateFile
        }

        // Remove the old backing file when the title/project changed (rename).
        val oldPath = previousFilePath ?: todo.filePath
        if (oldPath.isNotBlank()) {
            val oldFile = File(vaultDir, oldPath)
            if (oldFile.exists() && oldFile.canonicalPath != newFile.canonicalPath) {
                oldFile.delete()
            }
        }

        val relativePath = newFile.relativeTo(vaultDir).path.replace('\\', '/')

        val saved = todo.copy(filePath = relativePath, updatedAt = LocalDateTime.now())
        newFile.writeText(serializeToMarkdown(saved))
        return saved
    }

    /** Hard-deletes the markdown file backing [todo]. Prefer [trashTodo] for user-initiated deletes. */
    fun deleteTodo(todo: TodoItem) {
        if (todo.filePath.isBlank()) return
        File(vaultDir, todo.filePath).takeIf { it.exists() }?.delete()
    }

    /**
     * Moves [todo]'s backing file to [TRASH_FOLDER], preserving its relative project sub-path.
     * Returns the updated [TodoItem] with a [TodoItem.filePath] pointing to the trash location,
     * or the original [todo] unchanged if the source file doesn't exist.
     */
    fun trashTodo(todo: TodoItem): TodoItem {
        if (todo.filePath.isBlank()) return todo
        val sourceFile = File(vaultDir, todo.filePath)
        if (!sourceFile.exists()) return todo

        // Mirror sub-path under .trash — e.g. Tasks/Work/Report.md → Tasks/.trash/Work/Report.md
        val subPath = todo.filePath.removePrefix("$TASKS_FOLDER/")
        val destFile = File(trashDir, subPath)
        destFile.parentFile?.mkdirs()

        // Avoid name collision in trash by appending a timestamp suffix if needed.
        val finalDest = if (destFile.exists()) {
            val ts = System.currentTimeMillis()
            File(destFile.parentFile, "${destFile.nameWithoutExtension}-$ts.${destFile.extension}")
        } else destFile

        sourceFile.renameTo(finalDest)
        val newRelativePath = finalDest.relativeTo(vaultDir).path.replace('\\', '/')
        return todo.copy(filePath = newRelativePath, updatedAt = java.time.LocalDateTime.now())
    }

    /**
     * Moves a trashed [todo] back to its original location under [TASKS_FOLDER].
     * Returns the restored [TodoItem] with the updated [TodoItem.filePath].
     */
    fun restoreTodo(todo: TodoItem): TodoItem {
        if (todo.filePath.isBlank()) return todo
        val sourceFile = File(vaultDir, todo.filePath)
        if (!sourceFile.exists()) return todo

        // Reconstruct the original Tasks/ path from the .trash sub-path.
        val subPath = todo.filePath.removePrefix("$TRASH_FOLDER/")
        val destFile = File(tasksDir, subPath)
        destFile.parentFile?.mkdirs()

        val finalDest = if (destFile.exists()) {
            val ts = System.currentTimeMillis()
            File(destFile.parentFile, "${destFile.nameWithoutExtension}-restored-$ts.${destFile.extension}")
        } else destFile

        sourceFile.renameTo(finalDest)
        val newRelativePath = finalDest.relativeTo(vaultDir).path.replace('\\', '/')
        return todo.copy(filePath = newRelativePath, updatedAt = java.time.LocalDateTime.now())
    }

    /** Returns all [TodoItem]s currently in the trash folder. */
    fun getTrashedTodos(): List<TodoItem> {
        if (!trashDir.exists()) return emptyList()
        return getAllMarkdownFiles(trashDir).mapNotNull { parseMarkdownFile(it) }
    }

    /**
     * Hard-deletes all trash items whose [TodoItem.updatedAt] (the date they were trashed)
     * is older than [TRASH_RETENTION_DAYS] days.
     * @return the number of files purged.
     */
    fun purgeOldTrash(): Int {
        if (!trashDir.exists()) return 0
        val cutoff = java.time.LocalDateTime.now().minusDays(TRASH_RETENTION_DAYS)
        var count = 0
        getAllMarkdownFiles(trashDir).forEach { file ->
            val item = parseMarkdownFile(file) ?: return@forEach
            if (item.updatedAt.isBefore(cutoff)) {
                file.delete()
                count++
            }
        }
        // Remove empty directories left behind.
        cleanEmptyDirs(trashDir)
        return count
    }

    private fun cleanEmptyDirs(dir: File) {
        dir.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
            cleanEmptyDirs(sub)
            if (sub.list()?.isEmpty() == true) sub.delete()
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Parses a single markdown file into a [TodoItem].
     * Returns `null` if the file cannot be read or is malformed beyond recovery.
     */
    fun parseMarkdownFile(file: File): TodoItem? {
        return try {
            val content = file.readText()
            val frontmatter = parseFrontmatter(content)
            val body = parseBody(content)
            val relativePath = file.relativeTo(vaultDir).path
            
            // Parse checklist items from body (Track E1)
            val checklist = parseChecklistFromBody(body)
            val bodyWithoutChecklist = body.replace(Regex("^- \\[[ xX]\\] .*$", RegexOption.MULTILINE), "").trim()

            TodoItem(
                id = frontmatter["id"]?.takeIf { it.isNotBlank() } ?: relativePath,
                title = frontmatter["title"]?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                content = bodyWithoutChecklist,
                dueDate = parseDate(frontmatter["due"]),
                priority = Priority.fromString(frontmatter["priority"]),
                location = frontmatter["location"]?.takeIf { it.isNotBlank() },
                tags = parseTags(frontmatter["tags"]),
                isCompleted = frontmatter["status"]?.lowercase()?.trim() == "done",
                filePath = relativePath,
                createdAt = parseDateTime(frontmatter["created"]) ?: fileCreationTime(file),
                updatedAt = parseDateTime(frontmatter["updated"]) ?: LocalDateTime.now(),
                checklist = checklist,
                recurrence = frontmatter["recurrence"]?.lowercase()?.trim() ?: "none",
                loe = frontmatter["loe"]?.trim()?.toIntOrNull() ?: 0,
                reminderEnabled = frontmatter["reminderEnabled"]?.trim()?.toBooleanStrictOrNull() ?: true,
                reminderTime = frontmatter["reminderTime"]?.trim()?.let {
                    runCatching { LocalTime.parse(it) }.getOrNull()
                }
            )
        } catch (e: Exception) {
            null
        }
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    /** Converts a [TodoItem] to its Obsidian-compatible markdown string representation. */
    fun serializeToMarkdown(todo: TodoItem): String = buildString {
        appendLine(FRONTMATTER_DELIMITER)
        appendLine("id: ${encodeScalar(todo.id)}")
        appendLine("title: ${encodeScalar(todo.title)}")
        appendLine("status: ${if (todo.isCompleted) "done" else "open"}")
        appendLine("priority: ${todo.priority.label}")
        todo.dueDate?.let { appendLine("due: ${it.format(DATE_FORMATTER)}") }
        todo.location?.takeIf { it.isNotBlank() }?.let { appendLine("location: ${encodeScalar(it)}") }
        if (todo.tags.isNotEmpty()) {
            appendLine("tags:")
            todo.tags.forEach { appendLine("  - ${encodeScalar(it)}") }
        }
        if (todo.recurrence != "none") appendLine("recurrence: ${encodeScalar(todo.recurrence)}")
        if (todo.loe != 0) appendLine("loe: ${todo.loe}")
        appendLine("reminderEnabled: ${todo.reminderEnabled}")
        todo.reminderTime?.let { appendLine("reminderTime: $it") }
        appendLine("created: ${todo.createdAt.format(DATETIME_FORMATTER)}")
        appendLine("updated: ${todo.updatedAt.format(DATETIME_FORMATTER)}")
        appendLine(FRONTMATTER_DELIMITER)
        
        // Emit checklist items as markdown checkboxes (Track E1)
        if (todo.checklist.isNotEmpty()) {
            todo.checklist.forEach { item ->
                val checkbox = if (item.isDone) "[x]" else "[ ]"
                appendLine("- $checkbox ${item.text}")
            }
        }
        
        if (todo.content.isNotBlank()) {
            if (todo.checklist.isNotEmpty()) appendLine() // separator if checklist exists
            append(todo.content)
            if (!todo.content.endsWith("\n")) appendLine()
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Parses the YAML frontmatter between the first pair of `---` delimiters.
     *
     * Supports scalar values (`key: value`) and simple block sequences:
     * ```yaml
     * tags:
     *   - foo
     *   - bar
     * ```
     * Sequences are stored as a single comma-separated value under their key.
     */
    internal fun parseFrontmatter(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = content.lines()

        // Must start with ---
        if (lines.isEmpty() || lines[0].trim() != FRONTMATTER_DELIMITER) return result

        val closingIndex = lines.drop(1).indexOfFirst { it.trim() == FRONTMATTER_DELIMITER }
        if (closingIndex == -1) return result

        val frontmatterLines = lines.subList(1, closingIndex + 1)
        var currentListKey: String? = null
        val listAccumulator = mutableListOf<String>()

        for (line in frontmatterLines) {
            when {
                // Block sequence item (indent + dash)
                line.matches(Regex("\\s+- .*")) || line.matches(Regex("- .*")) -> {
                    if (currentListKey != null) {
                        val value = decodeScalar(line.trimStart().removePrefix("- ").trim())
                        listAccumulator.add(value)
                        result[currentListKey] = listAccumulator.joinToString(",")
                    }
                }
                // Key: value line
                line.contains(':') && !line.startsWith(" ") && !line.startsWith("\t") -> {
                    // Flush pending list
                    if (currentListKey != null && listAccumulator.isNotEmpty()) {
                        result[currentListKey] = listAccumulator.joinToString(",")
                        listAccumulator.clear()
                        currentListKey = null
                    }
                    val colonIdx = line.indexOf(':')
                    val key = line.substring(0, colonIdx).trim()
                    val value = decodeScalar(line.substring(colonIdx + 1).trim())
                    if (value.isEmpty()) {
                        // Possible block sequence follows
                        currentListKey = key
                    } else {
                        currentListKey = null
                        result[key] = value
                    }
                }
            }
        }
        return result
    }

    /** Returns the markdown body (everything after the closing `---` delimiter). */
    internal fun parseBody(content: String): String {
        val lines = content.lines()
        if (lines.isEmpty() || lines[0].trim() != FRONTMATTER_DELIMITER) return content
        val closingIndex = lines.drop(1).indexOfFirst { it.trim() == FRONTMATTER_DELIMITER }
        if (closingIndex == -1) return content
        return lines.drop(closingIndex + 2).joinToString("\n").trimStart()
    }

    private fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try { LocalDate.parse(value.trim(), DATE_FORMATTER) } catch (_: DateTimeParseException) { null }
    }

    private fun parseDateTime(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try { LocalDateTime.parse(value.trim(), DATETIME_FORMATTER) } catch (_: DateTimeParseException) { null }
    }

    private fun parseTags(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun encodeScalar(value: String): String {
        val requiresQuotes = value.isEmpty() ||
            value != value.trim() ||
            value.startsWith("- ") ||
            value.startsWith("? ") ||
            value.startsWith(": ") ||
            value.startsWith("---") ||
            value.startsWith("\"") ||
            value.contains(": ") ||
            value.any { it == '#' || it.code < 0x20 }
        if (!requiresQuotes) return value

        return buildString(value.length + 2) {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
            append('"')
        }
    }

    private fun decodeScalar(value: String): String {
        if (value.length < 2) return value
        if (value.first() == '\'' && value.last() == '\'') {
            return value.substring(1, value.lastIndex).replace("''", "'")
        }
        if (value.first() != '"' || value.last() != '"') return value

        val source = value.substring(1, value.lastIndex)
        return buildString(source.length) {
            var index = 0
            while (index < source.length) {
                val char = source[index++]
                if (char != '\\' || index == source.length) {
                    append(char)
                    continue
                }
                when (val escaped = source[index++]) {
                    '\\' -> append('\\')
                    '"' -> append('"')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    'u' -> {
                        val end = (index + 4).coerceAtMost(source.length)
                        val code = source.substring(index, end).toIntOrNull(16)
                        if (end - index == 4 && code != null) {
                            append(code.toChar())
                            index = end
                        } else {
                            append("\\u")
                        }
                    }
                    else -> {
                        append('\\')
                        append(escaped)
                    }
                }
            }
        }
    }

    private fun fileCreationTime(file: File): LocalDateTime {
        val attributes = try {
            Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        } catch (_: Exception) {
            null
        }
        val fileTime = attributes?.creationTime()
            ?.takeIf { it.toMillis() > 0L }
            ?: attributes?.lastModifiedTime()
        val instant = fileTime?.toInstant()
            ?: java.time.Instant.ofEpochMilli(file.lastModified())
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    }

    /** Extracts checklist items from markdown body (Track E1). Parses `- [ ]` / `- [x]` syntax. */
    private fun parseChecklistFromBody(body: String): List<ChecklistItem> =
        Regex("^- \\[([ xX])] (.*)$", RegexOption.MULTILINE)
            .findAll(body)
            .map { match ->
                val isChecked = match.groupValues[1].equals("x", ignoreCase = true)
                val text = match.groupValues[2]
                ChecklistItem(text, isChecked)
            }
            .toList()
}
