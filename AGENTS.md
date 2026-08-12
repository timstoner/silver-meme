# AGENTS.md

Guidance for AI coding agents working in this repository (SilverMeme — an Android Obsidian-vault TODO manager).

## Architecture (data flow, not just folders)

`UI (Compose screens) → ViewModel → TodoRepository (TodoDataSource) → {MarkdownFileManager, GitRepository}`

- **`MarkdownFileManager`** (`data/storage/`) — pure file I/O. Reads/writes `TodoItem`s as Obsidian-style markdown files with YAML frontmatter. Supports nested project folders under `<vault>/Tasks/` (e.g. `Tasks/Work/Report.md`) and soft-deletes via `Tasks/.trash/` with 30-day retention. It has **no knowledge of git or Android Context** — it's a plain `File`-based class, which is why it's fully unit-testable with `TemporaryFolder` (see `MarkdownFileManagerTest.kt`).
- **`TodoDataSource`** (interface in `data/repository/`) — abstraction layer for todo CRUD, trash, bulk operations, and sync. `TodoRepository` is the primary implementation; allows future swapping (e.g., database-backed). Defines contract for auto-sync behavior: every write method calls `syncIfConfigured()`.
- **`GitRepository`** (`data/repository/`) — pure JGit wrapper (clone/pull/commit/push). No knowledge of markdown or settings. Returns a sealed `GitResult` (`Success` / `Error`) instead of throwing, so callers never need try/catch for git-specific failures.
- **`TodoRepository`** is the *coordinator/single source of truth*: resolves the vault directory from `SettingsStore` (falls back to `context.getExternalFilesDir("vault")`), constructs a fresh `MarkdownFileManager` per call, and **auto-pushes after every save/delete/trash** via `syncIfConfigured()` — but only if a git remote URL is configured. Implements `TodoDataSource`. This "auto-push on write" behavior is a core product feature, not incidental — don't remove it when touching CRUD methods. Supports bulk operations (`bulkSave`, `bulkTrash`) that sync once at the end.
- **`SettingsStore`** (interface in `data/storage/`) — abstraction for persisted app settings (git URL, username, PAT, vault path, author info, theme, filter state). Primary implementation is `SettingsDataStore` backed by Jetpack DataStore. All values are `Flow<String>` — read one-shot via `.first()` in suspend functions.
- **`SilverMemeApplication`** acts as a minimal manual service locator (no DI framework like Hilt/Koin). Dependencies (`SettingsDataStore`, `TodoRepository`) are `by lazy` singletons exposed as `application` properties, injected into ViewModels via custom `Factory` classes (see `TodoViewModel.Factory`, `SettingsViewModel.Factory`) and wired up in `MainActivity.onCreate`. When adding a new ViewModel, follow this same Factory + Application-locator pattern rather than introducing a DI library.

## Critical conventions specific to this project

- **Markdown is the persistence format** — `TodoItem` is never serialized as JSON. `MarkdownFileManager.serializeToMarkdown`/`parseFrontmatter` implement a *minimal custom YAML subset* (scalars + simple `- item` block sequences only; no nested maps/quoting). If you need a new frontmatter field, add parsing in `parseFrontmatter`, extraction in `parseMarkdownFile`, and emission in `serializeToMarkdown` — all three must stay in sync.
  - **New frontmatter fields** (Phase 2–E features): `recurrence` (recurrence pattern string, e.g. "daily", "weekly:MON,WED", "monthly:15", "every:3:days"), `loe` (integer, Level of Effort: 0/1/2/3/5/8/13). Optional fields are only emitted if non-default (recurrence != "none", loe != 0).
  - **Checklist items** (Track E1) are persisted as markdown `- [ ]` / `- [x]` lines immediately after the frontmatter, not as a frontmatter field. Parsed out in `parseChecklistFromBody()` and re-rendered in `serializeToMarkdown()`.
- **Projects and nested folders** (Track E3): `TodoItem.filePath` can be `Tasks/Work/Report.md` (nested), and `TodoItem.project` property derives the intermediate path ("Work"). `MarkdownFileManager.saveTodo()` creates parent directories as needed. Filename remains sanitized to `sanitizeFilename()` rules regardless of nesting.
- **Filename = sanitized title** (`sanitizeFilename`, capped at 150 chars, strips `/\:*?"<>|#`). Renaming a task's title deletes the old `.md` file and writes a new one (see `saveTodo` in `MarkdownFileManager`) — `TodoItem.filePath` is the mechanism used to detect/track the old file.
- **`id` defaults to the filename** (without extension) when frontmatter has no `id` field — don't assume `id` is always a UUID; existing vaults without an `id:` line are still valid.
- **Trash and soft-delete**: Tasks are moved to `Tasks/.trash/` (not hard-deleted) via `TodoDataSource.trashTodo()`. Trash items are retained for 30 days, then auto-purged by `purgeOldTrash()` (can be called from settings or as a background job). Restore moves files back to `Tasks/`.
- **Recurrence parsing** (Phase E2): `RecurrenceRule.parse()` handles legacy plain strings ("daily", "weekly", "monthly", "none") and new formats ("weekly:MON,WED,FRI", "monthly:15", "every:3:days"). Store the result back as `todo.recurrence` string in frontmatter. Serialization uses `RecurrenceRule.toStorageString()` for round-tripping.
- **Git errors never surface as exceptions** — always return/propagate `GitRepository.GitResult`, and treat "no remote configured" as a silent success (`GitResult.Success`), not an error, to avoid bothering local-only users (see `pull()`/`push()` in `TodoRepository`).
- Network security: `usesCleartextTraffic="false"` in `AndroidManifest.xml` — HTTPS-only git remotes are enforced at the OS level. PAT tokens are stored via DataStore (app-private), never logged.

## Developer workflows

- **Build**: standard Gradle Android project (Kotlin DSL). AGP 9.2.1, Kotlin 2.2.10, compileSdk 35, targetSdk 35, minSdk 26. Compose is enabled via the `org.jetbrains.kotlin.plugin.compose` plugin (not the old `composeOptions.kotlinCompilerExtensionVersion`). WorkManager 2.10.0 for scheduled notifications.
- **Run unit tests**: `./gradlew :app:test` — this is the primary automated check in this repo (no CI config present). Tests live in `app/src/test/...` and only cover `MarkdownFileManager`/`Priority` since those are the only pure-Kotlin, non-Android-dependent classes. `GitRepository` and `TodoRepository` have no unit tests (they require Android `Context` / real git remotes) — don't assume test coverage there.
- If `gradlew` fails due to a missing wrapper jar, regenerate it: `gradle wrapper --gradle-version 8.4`.
- There is no lint/CI pipeline configured in this repo — verify changes by running the test task above and a Gradle build (`./gradlew :app:assembleDebug`).

## Key files to read before extending features

- `data/storage/MarkdownFileManager.kt` — the frontmatter format contract, trash/restore logic, project folder nesting, and checklist parsing.
- `data/storage/SettingsStore.kt` — interface for persisted settings (git, vault, author, theme, filter state).
- `data/repository/TodoDataSource.kt` — contract for todo CRUD, trash operations, bulk mutations, and sync.
- `data/repository/TodoRepository.kt` — where storage and git sync are coordinated; implements `TodoDataSource`.
- `data/model/Recurrence.kt` — `RecurrenceRule` and `RecurrenceFrequency` enums (Track E2); parsing, serialization, and next-due-date computation.
- `data/model/TodoItem.kt` — primary domain model; includes `checklist`, `recurrence`, `loe`, and `project` property.
- `notifications/NotificationScheduler.kt` — WorkManager scheduling for reminder notifications at task due dates.
- `viewmodel/TodoViewModel.kt` — filtering/sorting logic (`filteredTodos()`) is applied client-side over the full in-memory list each time; there's no persisted/derived filtered state.
- `ui/navigation/NavGraph.kt` — route constants (`Routes` object) for list/new/edit/settings/trash screens; edit route carries `todoId` as a nav argument.
- `ui/screens/TrashScreen.kt` — soft-deleted tasks; restore and permanent delete actions.
- `util/FilterStateSerializer.kt` — serialization of client-side filter/sort preferences (persisted in settings for UI continuity).

