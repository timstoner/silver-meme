# AGENTS.md

Guidance for AI coding agents working in this repository (SilverMeme — an Android Obsidian-vault TODO manager).

## Architecture (data flow, not just folders)

`UI (Compose screens) → ViewModel → TodoRepository → {MarkdownFileManager, GitRepository}`

- **`MarkdownFileManager`** (`data/storage/`) — pure file I/O. Reads/writes `TodoItem`s as Obsidian-style markdown files with YAML frontmatter, one file per task under `<vault>/Tasks/*.md`. It has **no knowledge of git or Android Context** — it's a plain `File`-based class, which is why it's fully unit-testable with `TemporaryFolder` (see `MarkdownFileManagerTest.kt`).
- **`GitRepository`** (`data/repository/`) — pure JGit wrapper (clone/pull/commit/push). No knowledge of markdown or settings. Returns a sealed `GitResult` (`Success` / `Error`) instead of throwing, so callers never need try/catch for git-specific failures.
- **`TodoRepository`** is the *coordinator/single source of truth*: resolves the vault directory from `SettingsDataStore` (falls back to `context.getExternalFilesDir("vault")`), constructs a fresh `MarkdownFileManager` per call, and **auto-pushes after every save/delete** via `syncIfConfigured()` — but only if a git remote URL is configured. This "auto-push on write" behavior is a core product feature, not incidental — don't remove it when touching CRUD methods.
- **`SilverMemeApplication`** acts as a minimal manual service locator (no DI framework like Hilt/Koin). Dependencies (`SettingsDataStore`, `TodoRepository`) are `by lazy` singletons exposed as `application` properties, injected into ViewModels via custom `Factory` classes (see `TodoViewModel.Factory`, `SettingsViewModel.Factory`) and wired up in `MainActivity.onCreate`. When adding a new ViewModel, follow this same Factory + Application-locator pattern rather than introducing a DI library.
- All settings (git remote URL, username, PAT token, vault path, author name/email) are `Flow<String>` from Jetpack DataStore — read one-shot via `.first()` in suspend functions (see every method in `TodoRepository`/`GitRepository`).

## Critical conventions specific to this project

- **Markdown is the persistence format** — `TodoItem` is never serialized as JSON. `MarkdownFileManager.serializeToMarkdown`/`parseFrontmatter` implement a *minimal custom YAML subset* (scalars + simple `- item` block sequences only; no nested maps/quoting). If you need a new frontmatter field, add parsing in `parseFrontmatter`, extraction in `parseMarkdownFile`, and emission in `serializeToMarkdown` — all three must stay in sync.
- **Filename = sanitized title** (`sanitizeFilename`, capped at 150 chars, strips `/\:*?"<>|#`). Renaming a task's title deletes the old `.md` file and writes a new one (see `saveTodo` in `MarkdownFileManager`) — `TodoItem.filePath` is the mechanism used to detect/track the old file.
- **`id` defaults to the filename** (without extension) when frontmatter has no `id` field — don't assume `id` is always a UUID; existing vaults without an `id:` line are still valid.
- **Git errors never surface as exceptions** — always return/propagate `GitRepository.GitResult`, and treat "no remote configured" as a silent success (`GitResult.Success`), not an error, to avoid bothering local-only users (see `pull()`/`push()` in `TodoRepository`).
- Network security: `usesCleartextTraffic="false"` in `AndroidManifest.xml` — HTTPS-only git remotes are enforced at the OS level. PAT tokens are stored via DataStore (app-private), never logged.

## Developer workflows

- **Build**: standard Gradle Android project (Kotlin DSL). AGP 9.2.1, Kotlin 2.2.10, compileSdk 34, minSdk 26. Compose is enabled via the `org.jetbrains.kotlin.plugin.compose` plugin (not the old `composeOptions.kotlinCompilerExtensionVersion`).
- **Run unit tests**: `./gradlew :app:test` — this is the primary automated check in this repo (no CI config present). Tests live in `app/src/test/...` and only cover `MarkdownFileManager`/`Priority` since those are the only pure-Kotlin, non-Android-dependent classes. `GitRepository` and `TodoRepository` have no unit tests (they require Android `Context` / real git remotes) — don't assume test coverage there.
- If `gradlew` fails due to a missing wrapper jar, regenerate it: `gradle wrapper --gradle-version 8.4`.
- There is no lint/CI pipeline configured in this repo — verify changes by running the test task above and a Gradle build (`./gradlew :app:assembleDebug`).

## Key files to read before extending features

- `data/storage/MarkdownFileManager.kt` — the frontmatter format contract.
- `data/repository/TodoRepository.kt` — where storage and git sync are stitched together.
- `viewmodel/TodoViewModel.kt` — filtering/sorting logic (`filteredTodos()`) is applied client-side over the full in-memory list each time; there's no persisted/derived filtered state.
- `ui/navigation/NavGraph.kt` — route constants (`Routes` object) for list/new/edit/settings screens; edit route carries `todoId` as a nav argument.

