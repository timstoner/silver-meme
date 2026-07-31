# SilverMeme — Code Review (2026-07-31)

Scope: full repository review of `app/src/main` + build/manifest config, with an
emphasis on **Android UI performance** and **usability** best practices.
No code was changed as part of this review.

Severity key: **P0** = data loss / crash / security · **P1** = broken or dead
feature, significant UX harm · **P2** = performance / polish · **P3** = nit.

---

## 1. Correctness & data integrity

### P0-1 — Editing a task destroys its checklist
`TodoDetailScreen.buildTodo()` constructs a brand-new `TodoItem` and never
copies `existingTodo.checklist`. `MarkdownFileManager.parseMarkdownFile` strips
`- [ ]` lines out of `content` (into `checklist`), and `serializeToMarkdown`
re-emits them from `checklist`. Net effect: **every edit-and-save silently
deletes all subtasks** from the markdown file. There is also no checklist editor
in the detail screen at all, so Track E1 is effectively half-landed.
*Fix:* pass `checklist = existingTodo?.checklist ?: emptyList()` and add the editor UI.

### P0-2 — Completing a recurring task overwrites the original file
`TodoViewModel.toggleComplete` builds `nextOccurrence = todo.copy(id = newUuid, …)`
but keeps the **same `filePath`**. `MarkdownFileManager.saveTodo` honours
`filePath` when it contains `/`, so the next occurrence is written over the file
that was just marked done — the completed record is lost, and the "done" history
disappears from the vault.
*Fix:* null/derive the path for the new occurrence (or derive filename from
title + due date) so a new file is created.

### P0-3 — Concurrent save race in `toggleComplete`
`saveTodo()` launches its own coroutine; `toggleComplete` calls it twice in a
row. Two concurrent `saveTodo → loadTodos` pipelines race, each triggering an
independent git commit/push. Result is non-deterministic list state and up to
two pushes per tap. `saveTodo` should expose a suspend variant used internally,
and recurrence spawning should be sequential.

### P0-4 — Two divergent filename sanitizers
`MarkdownFileManager.sanitizeFilename` replaces illegal chars with `_`;
`TodoDetailScreen.sanitizeForFilename` **deletes** them (and it trims/`take(150)`
in a different order). The detail screen builds `filePath` itself, so a title
containing `:` `/` `?` produces a path that does not match what the file manager
would produce — leading to stale-file deletion failures and duplicate `.md`
files. Filename derivation must live in exactly one place (the storage layer).

### P1-5 — Frontmatter has no escaping/quoting
`serializeToMarkdown` writes raw values. A title containing `: `, a leading
`- `, a leading `---`, or a newline produces a file that `parseFrontmatter`
mis-parses or that truncates the frontmatter block. The round-trip test harness
should include adversarial titles. Minimum viable fix: quote scalars that
contain `:`/`#`/leading dashes and reject/escape newlines.

### P1-6 — Duplicate `id` can crash the list
`id` falls back to `file.nameWithoutExtension`. With Track E3 subfolders,
`Tasks/Work/Report.md` and `Tasks/Home/Report.md` both yield id `Report`.
`LazyColumn(items(key = { it.id }))` throws `IllegalArgumentException` on
duplicate keys. Key on `filePath` (unique by construction) or dedupe on load.

### P1-7 — `fileCreationTime()` is a stub
It returns `LocalDateTime.now()` in both branches, so legacy files without a
`created:` line get today's date, and `Newest first` sort is meaningless for
them. Use `Files.readAttributes(...).creationTime()` / `lastModified()`.

### P2-8 — `parseChecklistFromBody` false positives
`match.groupValues[0].contains("[x")` inspects the whole line, so a task whose
text contains `[x` is marked done. Match on group 1 of the checkbox itself.

### P2-9 — Detail screen state is not saveable
All form fields use `remember { mutableStateOf(...) }`, not `rememberSaveable`,
and none are keyed on `existingTodo`. Rotating the device or a background
process-death **loses everything the user typed**, and if the todo list loads
after the edit screen composes, the form stays empty. This is one of the most
user-visible defects in the app.

### P2-10 — Edit route resolves the todo from a stale snapshot
`NavGraph` reads `todoViewModel.uiState.value.todos.firstOrNull { … }` once, at
composition time, outside of state observation. On a cold start / deep link the
list is empty, so `existingTodo` is `null` and the user silently gets a blank
"New Task" form under an "Edit Task" title.

---

## 2. Dead / unwired features

| Feature | Status |
|---|---|
| `NotificationScheduler` | Instantiated in `SilverMemeApplication`, **never called**. No reminder is ever scheduled or cancelled. `POST_NOTIFICATIONS` is declared but never requested at runtime, and `SCHEDULE_EXACT_ALARM` is declared but unused. |
| `ConflictResolutionDialog` | Never referenced by any screen. `SyncState.Conflict` is produced by the ViewModel but the `LaunchedEffect` in `TodoListScreen` falls into `else -> Unit`, so the app parks in `Conflict` forever with no message and no `clearSyncState()`. |
| Bulk selection (`selectedIds`, `toggleSelection`, `selectAll`) | ViewModel-only. `TodoItemCard` accepts `isSelected`/`onSelectionToggle` but `TodoListScreen` never passes them. No contextual action bar, no batch repository methods. Track B2/F are stubs. |
| `GitRepository.initLocal` | Never called — local-only users get no git repo, so `commitAndPush` would fail if a remote were later added. |
| `LevelOfEffort` capacity APIs (`getTodayLoe`, `getLoeCapacityPercent`) | Computed but never displayed anywhere. |
| `filteredTodos()` / `groupedTodos()` | Superseded by the reactive `visibleTodos`/`visibleGroups` flows; the imperative versions remain as a trap for future callers. |

---

## 3. Sync & security

- **P0 — Push without pull.** `TodoRepository.syncIfConfigured()` pushes
  directly. Any remote change since the last pull makes the push fail
  (non-fast-forward) and the error is swallowed — the user believes the task
  saved and synced. Track A1 was never implemented. Sync must be
  pull → rebase/merge → push.
- **P0 — PAT stored in plaintext.** `SettingsDataStore` persists the token in a
  plain preferences DataStore, and `AndroidManifest` sets
  `android:allowBackup="true"`, so the token can leave the device via
  auto-backup. Use `EncryptedSharedPreferences`/Keystore and set
  `allowBackup="false"` (or a `dataExtractionRules` exclusion).
- **P1 — Hard-reset semantics.** `GitRepository.pull` documents a hard reset on
  conflict but does not perform one; it reports `Conflict(status.conflicting +
  added + changed)`, which mixes ordinary local edits into the "conflict" list.
  `ResetCommand` is imported but unused.
- **P1 — No offline detection.** Every list entry triggers a network pull with
  no connectivity check, no retry, and no offline banner.
- **P2 — No git identity validation**: an empty/invalid author email silently
  produces commits from `silvermeme@local`.

---

## 4. UI performance (Compose)

1. **Full-screen spinner replaces the list on every refresh.** `uiState.isLoading`
   swaps the whole `LazyColumn` for a `CircularProgressIndicator`, and
   `LaunchedEffect(Unit) { syncFromRemote() }` runs on every navigation back to
   the list. The user sees the content flash away and rebuild after every save,
   every toggle, and every return from the detail screen. Keep the list mounted
   and show a top progress/refresh indicator instead.
2. **Every write re-reads and re-parses the entire vault.** `saveTodo`,
   `deleteTodo` and `toggleComplete` all call `loadTodos()`, which stats and
   parses every `.md` file recursively, then pushes to git. Checking a checkbox
   costs O(vault) file I/O plus a network round trip before the UI updates.
   Needs optimistic in-memory update + incremental reload (or a file-watched
   `Flow<List<TodoItem>>` cache).
3. **`viewModel.getAvailableProjects()` is called during composition** in both
   `TodoListScreen` and `TodoDetailScreen`. It is a plain function over the full
   list (map/filter/distinct/sorted) executed on **every recomposition**, and
   because it is not state-backed the drawer never updates when projects change.
   Should be a `StateFlow`/`derivedStateOf`.
4. **`collectAsState` instead of `collectAsStateWithLifecycle`.** All four
   collectors keep collecting while the app is backgrounded;
   `lifecycle-runtime-compose` is not even a dependency.
5. **Search has no debounce.** Each keystroke writes to DataStore (via
   `persistFilterState`) *and* re-filters + re-groups + re-sorts the full list.
   Debounce ~250 ms and skip persistence for transient query text.
6. **No `animateItem()`**, no `contentType` on `items`, and group headers are
   plain `item`s rather than `stickyHeader` — the list janks on reorder and
   loses context while scrolling long groups.
7. **`R8/minification disabled in release** (`isMinifyEnabled = false`) with JGit
   bundled: large APK, slower cold start, no resource shrinking. No baseline
   profile either.
8. **Recomposition scope**: `TodoListScreen` is one giant composable that reads
   `uiState`, `filterState`, `syncState` and `groups`; a sync-state change
   recomposes the entire drawer + top bar + list scaffold. Extract the top bar,
   drawer and list into separate composables that read only what they need.

---

## 5. Usability & accessibility

- **Rotation loses form input** (see P2-9) — highest-impact usability bug.
- **No pull-to-refresh** even though the empty state says "Tap + to create one,
  or pull to sync". Use `PullToRefreshBox`.
- **`SearchBar` misuse**: it is used with `active = false`, an empty content
  lambda and `onActiveChange = {}`. It renders as an oversized non-standard
  field and cannot show suggestions/history. A `DockedSearchBar` or a plain
  `TextField` with a clear button is correct here. There is also no visible
  "clear query" affordance inside the field.
- **Touch targets below 48 dp**: the completion `IconButton` is `Modifier.size(40.dp)`;
  the 14 dp status icons carry `contentDescription`s but sit inside no clickable.
- **Semantics**: `Card` uses `combinedClickable` without `onClickLabel`,
  `onLongClickLabel`, or `Role.Button`; the overdue state is conveyed by colour +
  a small icon only; `PriorityChip`/`LoeBadge` have no merged semantics, so
  TalkBack reads a card as a stream of disconnected fragments. Long-press to
  delete is undiscoverable and has no affordance hint.
- **Hardcoded English strings everywhere.** `strings.xml` already contains most
  of the needed keys but the screens ignore them — no localization, and RTL/
  pseudolocale testing is impossible. Also no plural resources ("1 day overdue"
  is hand-rolled).
- **Destructive actions are irreversible**: delete is a hard `File.delete()` with
  no undo snackbar and no trash (Track G4 not started).
- **No error recovery UI**: `uiState.errorMessage` renders as a raw red `Text`
  overlaid at the bottom of the list, never auto-clears, and has no retry.
- **Detail screen has no unsaved-changes guard** — back discards silently.
- **Theme**: `dynamicColor = true` by default means the custom brand palette is
  never used on Android 12+. Status bar is set with the deprecated
  `window.statusBarColor`, and the app is not edge-to-edge
  (`enableEdgeToEdge()` is required behaviour on Android 15+ with SDK 35).
- **No empty-state differentiation**: "No todos found" appears identically for
  "vault is empty", "filters hide everything", and "sync failed".

---

## 6. Build, config, tooling

| Item | Finding |
|---|---|
| `compileSdk`/`targetSdk` | 34 — below the Play Store minimum (35) since Aug 2025. |
| Java/Kotlin target | `VERSION_1_8` / `jvmTarget = "1.8"`; Java 8 desugaring is not enabled although `java.time` is used (safe at minSdk 26, but bump to 17 for AGP 9). |
| Dependencies | Compose BOM `2024.02.00`, core-ktx 1.12.0, nav 2.7.7, JGit 5.13 — all ~2 years stale; hardcoded versions instead of a version catalog (`libs.versions.toml` absent). |
| Missing deps | `lifecycle-runtime-compose`, `androidx.security:security-crypto`, `material3-adaptive`. |
| ProGuard | No JGit keep rules; enabling minify later will break reflection-based JGit services. |
| Lint | No `lint` config, no `lintOptions`, no detekt/ktlint. |
| Tests | Only `MarkdownFileManagerTest` + `TodoViewModelTest`. No tests for `TodoRepository`, `GitRepository`, `FilterStateSerializer`, `RecurrenceRule`, and zero Compose UI tests despite `ui-test-junit4` being on the classpath. |
| CI | `.github/workflows/android-test.yml` exists — verify it also runs `assembleDebug` and lint. |
| Manifest | `SCHEDULE_EXACT_ALARM` requested but unused (a Play policy risk); no `android:enableOnBackInvokedCallback="true"`. |

---

## 7. Architecture notes (non-blocking)

- The service-locator + Factory pattern is consistent and fine at this size.
- `TodoRepository` constructs a **new `MarkdownFileManager` on every call**,
  re-reading `vaultPath` from DataStore each time (`.first()` on a Flow per
  operation, 5 separate `.first()` reads inside `push()`). Cache a settings
  snapshot per operation.
- `TodoDataSource` is a good seam; extend it so `GitRepository` is also an
  interface, which unlocks unit tests for the sync sequencing fixes above.
- Business rules (filename derivation, recurrence advancement, project→path
  mapping) currently leak into the UI layer; pull them into the model/storage
  layer.

---

## 8. Suggested fix order

1. P0 data-loss set: checklist preservation, recurring-task overwrite, save race,
   single filename sanitizer.
2. Sync correctness: pull-before-push, conflict dialog wiring, encrypted PAT +
   `allowBackup=false`.
3. Usability core: `rememberSaveable` form state, optimistic UI + no full-screen
   spinner, pull-to-refresh, undo/trash.
4. Performance: state-backed project list, `collectAsStateWithLifecycle`,
   search debounce, list keys/`contentType`/`animateItem`, R8 + baseline profile.
5. Finish the stubbed features (notifications, bulk actions) — see
   `FEATURE_REQUIREMENTS.md`.
