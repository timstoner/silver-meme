package com.tmstoner.silvermeme.data.storage

import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
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
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        private const val FRONTMATTER_DELIMITER = "---"
    }

    private val tasksDir: File get() = File(vaultDir, TASKS_FOLDER)

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns all TODO items found in the Tasks folder. */
    fun getAllTodos(): List<TodoItem> {
        if (!tasksDir.exists()) return emptyList()
        return tasksDir
            .listFiles { f -> f.isFile && f.extension == "md" }
            ?.mapNotNull { parseMarkdownFile(it) }
            ?: emptyList()
    }

    /**
     * Persists [todo] to its backing markdown file.
     * The filename is derived from [TodoItem.title]; if the item was already
     * backed by a different file (title changed), the old file is removed.
     */
    fun saveTodo(todo: TodoItem): TodoItem {
        if (!tasksDir.exists()) tasksDir.mkdirs()

        val newFilename = "${sanitizeFilename(todo.title)}.md"
        val newFile = File(tasksDir, newFilename)

        // Remove old file if the title (and therefore filename) changed
        if (todo.filePath.isNotBlank()) {
            val oldFile = File(vaultDir, todo.filePath)
            if (oldFile.exists() && oldFile.canonicalPath != newFile.canonicalPath) {
                oldFile.delete()
            }
        }

        val relativePath = "$TASKS_FOLDER/$newFilename"
        val saved = todo.copy(filePath = relativePath, updatedAt = LocalDateTime.now())
        newFile.writeText(serializeToMarkdown(saved))
        return saved
    }

    /** Deletes the markdown file backing [todo], if it exists. */
    fun deleteTodo(todo: TodoItem) {
        if (todo.filePath.isBlank()) return
        File(vaultDir, todo.filePath).takeIf { it.exists() }?.delete()
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

            TodoItem(
                id = frontmatter["id"]?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                title = frontmatter["title"]?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                content = body,
                dueDate = parseDate(frontmatter["due"]),
                priority = Priority.fromString(frontmatter["priority"]),
                location = frontmatter["location"]?.takeIf { it.isNotBlank() },
                tags = parseTags(frontmatter["tags"]),
                isCompleted = frontmatter["status"]?.lowercase()?.trim() == "done",
                filePath = relativePath,
                createdAt = parseDateTime(frontmatter["created"]) ?: fileCreationTime(file),
                updatedAt = parseDateTime(frontmatter["updated"]) ?: LocalDateTime.now()
            )
        } catch (e: Exception) {
            null
        }
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    /** Converts a [TodoItem] to its Obsidian-compatible markdown string representation. */
    fun serializeToMarkdown(todo: TodoItem): String = buildString {
        appendLine(FRONTMATTER_DELIMITER)
        appendLine("id: ${todo.id}")
        appendLine("title: ${todo.title}")
        appendLine("status: ${if (todo.isCompleted) "done" else "open"}")
        appendLine("priority: ${todo.priority.label}")
        todo.dueDate?.let { appendLine("due: ${it.format(DATE_FORMATTER)}") }
        todo.location?.takeIf { it.isNotBlank() }?.let { appendLine("location: $it") }
        if (todo.tags.isNotEmpty()) {
            appendLine("tags:")
            todo.tags.forEach { appendLine("  - $it") }
        }
        appendLine("created: ${todo.createdAt.format(DATETIME_FORMATTER)}")
        appendLine("updated: ${todo.updatedAt.format(DATETIME_FORMATTER)}")
        appendLine(FRONTMATTER_DELIMITER)
        if (todo.content.isNotBlank()) {
            appendLine()
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
                        val value = line.trimStart().removePrefix("- ").trim()
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
                    val value = line.substring(colonIdx + 1).trim()
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

    private fun fileCreationTime(file: File): LocalDateTime =
        try { LocalDateTime.now() } catch (_: Exception) { LocalDateTime.now() }

    /** Strips characters not allowed in filenames on common OSes. */
    internal fun sanitizeFilename(title: String): String =
        title
            .replace(Regex("[/\\\\:*?\"<>|#]"), "_")
            .trim()
            .trimEnd('.')
            .take(150)
            .ifEmpty { "untitled" }
}
