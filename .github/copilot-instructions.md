# SilverMeme Copilot Instructions

## Project and commands

SilverMeme is a single-module Android app (`:app`) written in Kotlin with Jetpack Compose and Material 3. It requires JDK 17. The build uses AGP 9.3.1, Kotlin 2.4.10, compile/target SDK 35, and min SDK 26.

The shared Android emulator MCP server is configured in `.github/mcp.json`. Install Node.js 22.12+ and run `npm install --global agent-device@latest`; then start an Android emulator or connect an authorized device through `adb`.

Run commands from the repository root:

```bash
# All local unit tests; this is also the CI test command.
./gradlew :app:test

# One unit-test class.
./gradlew :app:testDebugUnitTest --tests "com.tmstoner.silvermeme.MarkdownFileManagerTest"

# One test method. Use a wildcard for Kotlin backticked names containing spaces.
./gradlew :app:testDebugUnitTest --tests "com.tmstoner.silvermeme.MarkdownFileManagerTest.parseFrontmatter*"

# Compile and package the debug application; this is also run in CI.
./gradlew :app:assembleDebug

# Android lint for the debug variant.
./gradlew :app:lintDebug
```

Unit tests use JUnit 4. `MarkdownFileManagerTest` uses `TemporaryFolder`; `TodoViewModelTest` uses coroutine test dispatchers and an in-memory `TodoDataSource` fake. Prefer these patterns over tests that require Android `Context` or a real Git remote.

## Architecture and ownership

The main data path is:

```text
Compose UI -> ViewModel -> TodoDataSource / TodoRepository
                         -> MarkdownFileManager (vault files)
                         -> GitRepository (JGit remote operations)
```

- `MarkdownFileManager` is pure `File`-based persistence for Obsidian-compatible task files under `<vault>/Tasks/`; do not give it Android, settings, or Git dependencies.
- `TodoRepository` is the coordinator and primary `TodoDataSource`. It resolves the configured vault path (or an app-private fallback), creates a fresh file manager per operation, refreshes widgets after relevant reads/writes, and coordinates Git synchronization.
- `GitRepository` is a JGit-only wrapper. Preserve its typed `GitResult` (`Success`, `Error`, `Conflict`) boundary rather than exposing Git exceptions to callers.
- `SettingsStore` abstracts settings as `Flow<String>` values; `SettingsDataStore` is its implementation. Repository methods use `.first()` for one-shot settings reads.
- `SilverMemeApplication` is the intentional manual service locator. It exposes lazy singleton dependencies, and `MainActivity` supplies them through each ViewModel's nested `Factory`. Do not introduce Hilt, Koin, or another DI framework.
- Compose screens consume `StateFlow` lifecycle-aware and send user actions to ViewModels. Keep storage, Git, and scheduling behavior out of composables. Navigation routes and deep links are centralized in `ui/navigation/NavGraph.kt`.

`TodoViewModel` holds the unfiltered list and applies filter/sort/grouping client-side. Persisted filter state is serialized through `SettingsStore`; do not add a second persisted/derived list to change this behavior.

## Markdown vault contract

`TodoItem` is persisted as markdown, never JSON. Each file has a small custom YAML-frontmatter subset plus a markdown body:

- Keep `MarkdownFileManager.parseFrontmatter`, `parseMarkdownFile`, and `serializeToMarkdown` synchronized when adding or changing a frontmatter field. The parser supports scalars and simple `- item` sequences only; do not assume a general YAML parser.
- Checklist entries belong immediately after frontmatter as `- [ ]` / `- [x]` markdown lines, not in frontmatter. Keep body parsing and rendering compatible.
- `recurrence` is stored as a compact string. Use `RecurrenceRule.parse()` and `toStorageString()` so legacy values (`daily`, `weekly`, `monthly`, `none`) and extended patterns round-trip.
- `loe` is optional frontmatter and is emitted only when nonzero. Valid points are `0, 1, 2, 3, 5, 8, 13`.
- Task paths are vault-relative. Nested projects use paths such as `Tasks/Work/Report.md`; `TodoItem.project` derives the intermediate path. Preserve nested folders through saves, trash, and restore.
- Filenames are derived only with `MarkdownFileManager.sanitizeFilename()` (150-character limit and invalid-character replacement). A title/project change must pass the previous file path so the stale backing file is removed.
- Existing vaults may omit `id`; parsing must retain the filename-based fallback ID.

User deletes are soft deletes: move files to `Tasks/.trash/`, preserve project subpaths, and restore them through the repository. Permanent deletion is reserved for trash operations. Old trash is purged after 30 days.

## Sync, reminder, and widget behavior

Every normal repository write (`saveTodo`, delete, trash, restore) preserves auto-sync when a Git remote is configured. Bulk mutations use `bulkSave` / `bulkTrash` to sync once. For a missing remote, pull/push is a successful no-op for local-only users.

Before an automatic push, `TodoRepository` pulls to integrate remote changes. Keep local writes successful even when that pull fails, and never log or expose the PAT. HTTPS-only transport is enforced by the manifest.

ViewModel task mutations also maintain side effects:

- Completion, trash, deletion, restore, and due-date edits must correctly schedule or cancel `NotificationScheduler` work.
- Completing a recurring dated task creates a new occurrence with a new ID and blank `filePath`, allowing collision-safe storage to create a fresh file.
- Repository writes and successful pulls refresh home-screen widgets. Widget deep links must remain aligned with `Routes` and manifest `silvermeme://todo/...` handling.

For Compose changes, preserve phone and tablet behavior: the list route uses `TabletTodoLayout` at medium-or-wider window classes and the standard list screen otherwise. Put user-visible strings in resources and retain accessibility semantics/content descriptions on interactive UI.
