# SilverMeme — Feature Roadmap & Delegation Plan

> Source: app review conducted 2026-07-30. This document breaks recommended
> work into **independent tracks** that can be delegated to separate agents
> and implemented concurrently, plus tracks that must be **serialized**
> because they touch the same files (notably the frontmatter contract in
> `MarkdownFileManager.kt` / `TodoItem.kt`).
>
> Each track lists: goal, rationale, files touched (ownership), dependencies,
> and acceptance criteria. Tracks marked **[PARALLEL-SAFE]** do not share
> file ownership with any other parallel-safe track and can be assigned to
> different agents/PRs at the same time. Tracks marked **[SERIALIZE]** share
> file ownership with sibling sub-tracks in the same group and must be done
> one at a time (or by a single agent) to avoid merge conflicts.

---

## How to use this document

1. Pick one track per agent/session. Do not split a single track across
   multiple concurrent agents — its file list is the unit of parallelism.
2. Before starting, re-read `AGENTS.md` for the project conventions
   (frontmatter 3-place rule, auto-push-on-write, `GitResult` error model,
   filename = sanitized title).
3. After finishing a track, run `./gradlew :app:test` and update
   `MarkdownFileManagerTest.kt` if the frontmatter schema changed.
4. Check off the track below and note the PR/commit reference.

---

## Track dependency graph

```
Track A (Sync Reliability & Security)     ─┐
Track B (List/Detail UI Polish)            │  all PARALLEL-SAFE, no shared files
Track C (Notifications & Reminders)        │  → can start immediately, together
Track D (Testing & CI Infrastructure)     ─┘

Track E (Frontmatter Schema Extensions)    → SERIALIZE internally (E1 → E2 → E3)
                                              start only after Track D adds the
                                              round-trip test harness (recommended,
                                              not strictly required)

Track F (Bulk Actions)                     → depends on Track B (list selection UI)
                                              being merged first (touches same
                                              TodoListScreen.kt region)

Track G (Tier 3 Advanced)                  → start after Tracks A–F stabilize;
                                              G1–G4 are mutually parallel-safe
```

---

## Track A — Sync Reliability & Security  [PARALLEL-SAFE]

**Owns:** `data/repository/GitRepository.kt`, `data/repository/TodoRepository.kt`,
`data/storage/SettingsDataStore.kt`, `viewmodel/TodoViewModel.kt` (sync-state
section only), new `ConflictResolutionDialog.kt`.

**Do not touch:** `TodoItem.kt`, `MarkdownFileManager.kt`, `TodoListScreen.kt`
selection/swipe UI, `TodoDetailScreen.kt`.

### A1. Pre-push pull / safer sync sequencing
- Change `TodoRepository.syncIfConfigured()` and the manual push path to pull
  before pushing (or clearly surface a warning when pull fails first).
- Add a `SyncState.Conflict` / `SyncState.Warning` case to `TodoViewModel`.
- Preserve the "no remote configured = silent success" contract.
- **Acceptance:** pushing after a stale local pull no longer silently
  overwrites remote changes; unit-testable via a fake `GitRepository`.

### A2. Conflict surfacing UI
- Add a `GitResult.Conflict(files: List<String>)` variant to `GitRepository`.
- Build `ConflictResolutionDialog` (Compose) offering "Keep local" / "Keep
  remote" per conflicting file, wired from `TodoListScreen.kt` only at the
  single call site where `SyncState.Failure`/`Conflict` is observed (minimal
  touch to that file — coordinate if Track B is also active there).
- **Acceptance:** a simulated merge conflict resolves via UI instead of a
  silent hard-reset or opaque error snackbar.

### A3. Encrypted credential storage
- Replace plaintext PAT/token storage in `SettingsDataStore` with
  `androidx.security.crypto.EncryptedSharedPreferences` (or Keystore-backed
  DataStore), migrating any existing plaintext value on first read.
- **Acceptance:** PAT is never stored or logged in plaintext; existing users'
  saved tokens still work after upgrade (one-time migration verified).

---

## Track B — List/Detail UI Polish  [PARALLEL-SAFE]

**Owns:** `ui/components/TodoItemCard.kt`, `ui/screens/TodoListScreen.kt`
(list-item interaction region only — avoid the sync/snackbar block owned by
Track A2).

**Do not touch:** ViewModel sync-state, `MarkdownFileManager.kt`, `TodoItem.kt`.

### B1. Swipe-to-complete / swipe-to-delete
- Use Compose `SwipeToDismissBox` on `TodoItemCard` rows in
  `TodoListScreen.kt`'s `LazyColumn`.
- Keep the existing long-press delete confirmation dialog as a fallback/undo
  path.
- **Acceptance:** swipe right completes/uncompletes, swipe left opens the
  existing delete confirmation; no regression to click/long-click handlers.

### B2. Multi-select mode (selection UI only — precursor to Track F)
- Add a "selection mode" toggled by long-press, with checkboxes overlaying
  `TodoItemCard`, a contextual top bar (count + bulk action icons), and
  `TodoViewModel` selection state (`selectedIds: Set<String>`).
- Wire bulk action *buttons* only — actual batched repository calls are
  Track F's responsibility (keeps this track's `TodoRepository.kt` footprint
  at zero).
- **Acceptance:** user can multi-select rows and see a contextual action bar;
  action buttons can be stubbed/no-op until Track F lands.

---

## Track C — Notifications & Reminders  [PARALLEL-SAFE]

**Owns:** new `notifications/` package (`NotificationScheduler.kt`,
`ReminderReceiver.kt`), `AndroidManifest.xml` (permission + receiver
declarations), `ui/screens/TodoDetailScreen.kt` (additive "Remind me" toggle
field only — new UI element, no edits to existing fields' logic),
`viewmodel/TodoViewModel.kt` (additive hook in `saveTodo`/`deleteTodo` to
schedule/cancel reminders — call a new scheduler method, don't modify existing
CRUD logic).

**Do not touch:** `MarkdownFileManager.kt`, `TodoItem.kt` schema (reuse the
existing `dueDate` field — do not add a new frontmatter field for this).

- Schedule a local notification via `WorkManager` or `AlarmManager` when a
  task's `dueDate` is set/changed, cancel it on completion/deletion.
- Request `POST_NOTIFICATIONS` runtime permission (Android 13+).
- **Acceptance:** creating/editing a task with a due date schedules a
  reminder; completing or deleting the task cancels it; no frontmatter schema
  change required.

---

## Track D — Testing & CI Infrastructure  [PARALLEL-SAFE]

**Owns:** `app/src/test/**` (new test files only), new `.github/workflows/*.yml`,
new PR template. May *add* thin seams (interfaces) to
`TodoRepository`/`GitRepository` for testability, but coordinate signature
changes with Track A if both are active — prefer additive constructor
parameters with defaults to avoid breaking Track A's edits.

- Extract fakes/interfaces so `TodoRepository` and `TodoViewModel` are unit
  testable without Android `Context` (e.g. inject a `MarkdownFileManager`
  factory lambda and a `GitRepository` interface).
- Add `TodoViewModel` tests for `filteredTodos()` and sync-state transitions.
- Add a round-trip test helper in `MarkdownFileManagerTest.kt`
  (serialize → parse → assert equality) that Track E must extend for every
  new frontmatter field.
- Add GitHub Actions workflow running `./gradlew :app:test` and
  `:app:assembleDebug` on every PR.
- **Acceptance:** `./gradlew :app:test` covers repository/viewmodel logic;
  CI runs automatically on PRs.

---

## Track E — Frontmatter Schema Extensions  [SERIALIZE — single owner/agent]

**Owns (exclusively, one sub-track at a time):** `data/model/TodoItem.kt`,
`data/storage/MarkdownFileManager.kt`, `app/src/test/.../MarkdownFileManagerTest.kt`,
plus the relevant editor UI in `TodoDetailScreen.kt` and display in
`TodoItemCard.kt`/`TodoListScreen.kt` for each sub-feature.

> ⚠️ These three sub-tracks all modify the same core files
> (`parseFrontmatter` / `parseMarkdownFile` / `serializeToMarkdown` /
> `TodoItem`). Run them **sequentially**, not concurrently, even across
> different agents — merge conflicts and frontmatter-contract regressions
> are otherwise likely. Recommended order: E1 → E2 → E3.

### E1. Subtasks / checklist items
- Add `checklist: List<ChecklistItem>` (text + done), rendered as Obsidian
  native `- [ ]` / `- [x]` lines in the markdown **body** (not frontmatter)
  for compatibility with Obsidian's own Tasks plugin.
- Add checklist editor to `TodoDetailScreen`; add a progress indicator
  ("2/5 done") to `TodoItemCard`.
- **Acceptance:** checklist round-trips through save/reload; existing tasks
  without checklists are unaffected.

### E2. Recurring tasks
- Add `recurrence` frontmatter field (`daily|weekly|monthly|none`).
- On completing a recurring task, `TodoViewModel.toggleComplete` creates the
  next occurrence (new file/due date) instead of just marking done.
- Add recurrence picker to `TodoDetailScreen`.
- **Acceptance:** completing a recurring task spawns the next occurrence with
  correctly advanced `dueDate`; non-recurring tasks behave exactly as before.

### E3. Projects / folders
- Support subfolders under `Tasks/` (e.g. `Tasks/Work/`) as a lightweight
  project concept.
- Change `MarkdownFileManager.getAllTodos()` to scan recursively; add a
  `project`/`folder` field to `TodoItem` derived from the relative path.
- Add project filter/tabs to `TodoListScreen` and a project picker to
  `TodoDetailScreen`.
- **Acceptance:** tasks in subfolders are discovered and filterable; flat
  `Tasks/*.md` vaults continue to work unchanged (default/no-project).

---

## Track F — Bulk Actions  [depends on Track B2]

**Owns:** `data/repository/TodoRepository.kt` (new batch methods only —
`saveTodos`/`deleteTodos` that push once at the end), `viewmodel/TodoViewModel.kt`
(wire selection actions from B2 to batch repository calls), `TodoListScreen.kt`
(wire the contextual action bar buttons added in B2).

- Batch complete/delete/tag/priority-change over `selectedIds` from Track B2,
  calling `syncIfConfigured()` only once per batch (not once per item) to
  avoid excessive auto-push churn.
- **Acceptance:** selecting 5 tasks and bulk-completing them results in
  exactly one git commit/push, not five.

---

## Track G — Tier 3 Advanced Features  [start after A–F land; G1–G4 parallel-safe]

### G1. Background periodic sync (WorkManager)
- Periodic pull + push-on-reconnect via `WorkManager`, toggle in Settings
  (additive, off by default) so it doesn't silently change the documented
  "auto-push on write" behavior.
- **Owns:** new `sync/SyncWorker.kt`, `SettingsDataStore.kt` (new toggle key),
  `SettingsScreen.kt` (new switch).

### G2. Multi-vault support
- Allow switching between multiple git-backed vaults.
- **Owns:** `SettingsDataStore.kt`, `TodoRepository.kt` vault resolution,
  new vault-picker UI. Largest-scope Tier 3 item — plan as its own phase.

### G3. Quick-add widget / App Shortcut
- Glance widget or `ShortcutManager` entry to create a task without opening
  the app.
- **Owns:** new `widget/` package, `AndroidManifest.xml` additions.

### G4. Trash / undo (soft delete)
- Move deleted files to `Tasks/.trash/` instead of hard delete; add "Undo"
  snackbar action; periodic purge.
- **Owns:** `MarkdownFileManager.deleteTodo`, `TodoRepository.deleteTodo`,
  `TodoViewModel`, `TodoListScreen.kt` snackbar action.
- Note: touches `MarkdownFileManager.kt`, so must not run concurrently with
  an active Track E sub-track.

### G5. Obsidian wikilink support (optional / lowest priority)
- `[[Note Name]]` parsing/autocomplete in the notes body field.
- **Owns:** `TodoDetailScreen.kt` notes field only.

---

## Suggested assignment for immediate concurrent kickoff

| Agent slot | Track | Files touched (exclusive) |
|---|---|---|
| 1 | A (Sync Reliability & Security) | GitRepository.kt, TodoRepository.kt, SettingsDataStore.kt, TodoViewModel.kt (sync section) |
| 2 | B (List/Detail UI Polish) | TodoItemCard.kt, TodoListScreen.kt (interaction region) |
| 3 | C (Notifications & Reminders) | new notifications/ package, AndroidManifest.xml, TodoDetailScreen.kt (additive), TodoViewModel.kt (additive hook) |
| 4 | D (Testing & CI Infrastructure) | test/** , .github/workflows/** |

Track E should be assigned to **one** agent only, run after slots 1–4 report
back (or in parallel if that agent avoids touching any file listed above).
Track F waits on Track B2. Track G waits on A–F.

---

## Progress tracking

- [ ] A1 — Pre-push pull / safer sync sequencing
- [ ] A2 — Conflict surfacing UI
- [ ] A3 — Encrypted credential storage
- [ ] B1 — Swipe-to-complete / swipe-to-delete
- [ ] B2 — Multi-select mode (selection UI)
- [ ] C — Notifications & reminders
- [ ] D — Testing & CI infrastructure
- [ ] E1 — Checklists / subtasks
- [ ] E2 — Recurring tasks
- [ ] E3 — Projects / folders
- [ ] F — Bulk actions
- [ ] G1 — Background periodic sync
- [ ] G2 — Multi-vault support
- [ ] G3 — Quick-add widget / shortcut
- [ ] G4 — Trash / undo
- [ ] G5 — Obsidian wikilink support

