# SilverMeme — Feature Requirements Document (FRD)

**Version:** 1.0 · **Date:** 2026-07-31 · **Status:** Proposed
**Companion document:** `CODE_REVIEW_2026-07-31.md` (defect findings)
**Supersedes planning in:** `FEATURE_ROADMAP.md` (tracks A–G; this FRD records
what actually landed and specifies the remaining and net-new capabilities)

---

## 1. Product context

SilverMeme is an Android task manager whose **system of record is a git-backed
Obsidian vault** of one-markdown-file-per-task. Two invariants govern every
requirement below:

- **INV-1 — Obsidian compatibility.** Anything the app writes must remain a
  valid, human-editable Obsidian note. New metadata goes in YAML frontmatter;
  anything Obsidian renders natively (checkboxes, links, tags) goes in the body.
- **INV-2 — The vault is shared and externally mutable.** The desktop user, and
  other devices, edit the same files. The app must never assume it is the only
  writer.

### 1.1 Current capability baseline (implemented)

Markdown CRUD, YAML frontmatter (id/title/status/priority/due/location/tags/
recurrence/loe/created/updated), git clone-pull-commit-push with PAT auth,
filtering (priority, completed, overdue, project, text search), 5 sort orders,
due-date grouping, persisted filter state, project subfolders, LOE estimates,
recurrence rules, dark/light/system theme, navigation drawer.

### 1.2 Gap summary

| Area | Gap |
|---|---|
| Reminders | Scheduler exists but is never invoked; no runtime permission flow |
| Bulk actions | Selection state exists in ViewModel; no UI, no batch persistence |
| Conflicts | Dialog exists; never shown; `SyncState.Conflict` is a dead end |
| Undo / trash | Deletes are permanent and unrecoverable |
| Subtasks | Parsed and serialized, but no editor and destroyed on save |
| Offline | No connectivity awareness, no queued sync, no offline indicator |
| Entry speed | No widget, no shortcut, no quick-add, no natural-language input |
| Insight | LOE capacity computed but never displayed; no calendar/agenda view |
| Attachments | No support for images/files referenced from a task |
| Multi-vault | Single hardcoded vault |

---

## 2. Requirement conventions

- **MUST** = required for the release the feature is assigned to.
- **SHOULD** = strongly recommended; may slip.
- **MAY** = optional/stretch.
- Each feature lists **AC** (acceptance criteria) that are independently testable.
- IDs are stable: `FR-<area><n>`.

---

## 3. Release plan

| Release | Theme | Features |
|---|---|---|
| **R1 — Trust** | Stop losing data; make sync honest | FR-S1, FR-S2, FR-S3, FR-D1, FR-D2, FR-U1 |
| **R2 — Daily driver** | Speed and comfort of everyday use | FR-U2, FR-U3, FR-T1, FR-T2, FR-N1, FR-P1 |
| **R3 — Power** | Bulk work, capture, insight | FR-B1, FR-C1, FR-C2, FR-V1, FR-V2 |
| **R4 — Reach** | Multi-context, integrations | FR-X1, FR-X2, FR-X3, FR-A1 |

---

## 4. R1 — Trust

### FR-S1 — Safe sync sequencing (pull → merge → push)
**Problem:** writes push without pulling; a stale local repo makes the push fail
non-fast-forward and the failure is swallowed, so the user believes their task
is synced when it is not.

**Requirements**
- The app MUST pull (fetch + merge) before every push initiated by a write.
- If the merge succeeds, the push MUST proceed automatically.
- If the pull fails for network reasons, the local write MUST still succeed and
  the change MUST be queued for the next successful sync (see FR-S3).
- Push failures MUST surface a persistent, actionable status (not a transient
  snackbar) showing "N changes not synced" with a retry action.
- "No remote configured" MUST continue to be treated as silent success.
- Commit messages SHOULD describe the change (`Add task: <title>`,
  `Complete task: <title>`, `Delete task: <title>`).

**AC**
1. With a remote commit made between two local saves, the second save
   integrates the remote commit and pushes without error.
2. With airplane mode on, saving a task succeeds locally, the pending badge
   shows `1`, and re-enabling network drains the queue automatically.
3. A rejected push leaves the UI in an explicit "not synced" state with retry.

---

### FR-S2 — Conflict detection and resolution
**Problem:** `SyncState.Conflict` is produced but never displayed; the app parks
in an unrecoverable state.

**Requirements**
- Merge conflicts MUST be detected per file and presented in a resolution UI.
- For each conflicting task the user MUST be able to choose **Keep mine**,
  **Keep theirs**, or **Keep both** (the local copy is written as
  `<title> (conflict <device> <timestamp>).md`).
- The UI MUST show a readable diff summary (title, status, due date, priority
  differences) rather than raw merge markers.
- The app MUST NOT perform a silent hard reset that discards local work.
- Resolution MUST produce a single commit and push.
- If the user dismisses the dialog, the conflict MUST remain visible as a
  persistent banner until resolved.

**AC**
1. Editing the same task on desktop and mobile then syncing shows the
   resolution dialog with both versions summarized.
2. "Keep both" produces two files, both visible in the list, no data lost.
3. No code path deletes local commits without explicit user confirmation.

---

### FR-S3 — Offline-first write queue and status
**Requirements**
- All writes MUST complete against local storage regardless of connectivity.
- The app MUST observe connectivity and automatically attempt a sync on
  reconnect (WorkManager, `NetworkType.CONNECTED` constraint).
- A sync status affordance MUST be visible from the list screen showing one of:
  `Synced <relative time>`, `N pending`, `Offline`, `Error — retry`.
- Sync MUST NOT be triggered on every navigation to the list; it MUST be
  throttled (default: at most once per 60 s, plus explicit pull-to-refresh).
- Users SHOULD be able to disable automatic sync entirely (manual-only mode).

**AC**
1. Navigating list → detail → list five times triggers at most one network pull.
2. Killing the app with pending changes and relaunching still shows and drains
   the queue.

---

### FR-D1 — Non-destructive editing (subtask preservation + full checklist editor)
**Problem:** saving an edited task deletes its checklist; completing a recurring
task overwrites the completed file.

**Requirements**
- Saving a task MUST preserve every field the editor does not expose.
- The detail screen MUST provide a checklist editor: add, edit inline, toggle,
  reorder (drag), and delete items.
- Checklist items MUST round-trip as Obsidian-native `- [ ]` / `- [x]` body
  lines (INV-1).
- The list card MUST show checklist progress (`3/5`) when a checklist exists.
- Completing a recurring task MUST create a **new file** for the next
  occurrence and leave the completed file intact.
- Checking every checklist item SHOULD offer (not force) completing the parent.

**AC**
1. Round-trip test: task with 5 checklist items → edit title → save → reload
   yields the same 5 items with identical done-state.
2. Completing a weekly recurring task yields two files: one `status: done` with
   the original due date, one `status: open` dated +7 days.

---

### FR-D2 — Trash, undo, and restore
**Requirements**
- Deleting a task MUST move its file to `Tasks/.trash/` (preserving relative
  project path), never hard-delete.
- An **Undo** snackbar action MUST be offered for at least 8 seconds after any
  delete or bulk delete, restoring the file to its original path.
- A **Trash** view MUST list deleted tasks with restore and "delete forever".
- Items in trash MUST be excluded from all lists, counts, filters, and search.
- Trash MUST auto-purge items older than a configurable retention (default 30
  days) on app start.
- `.trash/` contents MUST be committed like any other change (so deletion is
  recoverable from other devices too).

**AC**
1. Delete → Undo restores the task at its original project path with all fields.
2. Trash items never appear in the main list or in search results.
3. A 31-day-old trash item is purged and the purge is committed.

---

### FR-U1 — Input durability and edit-safety
**Requirements**
- All detail-screen form state MUST survive configuration change and process
  death (`rememberSaveable` / `SavedStateHandle`).
- Navigating back with unsaved changes MUST prompt: **Discard / Keep editing**.
- Opening an edit route before the task list has loaded MUST show a loading
  state and then populate — never an empty "New Task" form.
- Title validation MUST run on save and on blur, not only on save.
- Draft new tasks SHOULD be auto-persisted so an interrupted capture is
  recoverable.

**AC**
1. Type a title, rotate the device: the title is still there.
2. Cold-start deep link to `todo_edit/{id}` populates the correct task.

---

## 5. R2 — Daily driver

### FR-U2 — Responsive, non-blocking list
**Requirements**
- Toggling completion MUST update the UI optimistically (< 100 ms perceived),
  with rollback + error message on persistence failure.
- Refreshing/saving MUST NOT unmount the list; progress MUST be shown as a
  linear indicator or a pull-to-refresh spinner over the existing content.
- The list MUST support **pull-to-refresh** (`PullToRefreshBox`).
- Full-vault re-read after a single-item write MUST be eliminated in favour of
  an in-memory cache updated incrementally.
- The list MUST remain smooth (no dropped frames) with **1,000 tasks**:
  stable item keys, `contentType`, `animateItem()`, sticky group headers.
- Search input MUST be debounced (≈250 ms) and MUST NOT write to DataStore per
  keystroke.
- The app MUST use lifecycle-aware state collection.

**AC**
1. Scrolling a 1,000-task vault on a mid-tier device holds ≥ 55 fps (macrobenchmark).
2. Checking a checkbox reflects instantly with no list flicker; no full-screen
   spinner appears on save, toggle, or return from detail.

---

### FR-U3 — Gesture and multi-select interactions
**Requirements**
- Swipe right on a task MUST toggle completion; swipe left MUST delete (with
  FR-D2 undo). Both MUST show a coloured background with an icon while dragging.
- Long-press MUST enter multi-select mode with a contextual top bar showing the
  selected count and bulk actions; the existing long-press-to-delete shortcut
  MUST be replaced by this (delete becomes a bulk action).
- Selection state MUST survive rotation.
- Back / X MUST exit selection mode without acting.
- All gestures MUST have an equivalent non-gesture path for accessibility.

**AC**
1. Swiping performs the action and the undo snackbar restores prior state.
2. Long-press → select 3 → rotate → still 3 selected.

---

### FR-T1 — Due-date reminders
**Requirements**
- A task with a due date MUST schedule a local notification; the default
  reminder time MUST be user-configurable (default 09:00 local).
- Users MUST be able to set a per-task reminder offset (at time, 1 h before,
  1 day before, custom) and to disable the reminder for a task.
- Reminder configuration MUST NOT require a new frontmatter field that breaks
  Obsidian rendering; if stored, it MUST be an optional `reminder:` scalar.
- Editing or clearing a due date MUST reschedule/cancel; completing or deleting
  MUST cancel.
- `POST_NOTIFICATIONS` MUST be requested at runtime with a rationale screen, on
  first attempt to set a reminder — not at app launch.
- Notifications MUST deep-link to the task and offer **Complete** and
  **Snooze 1 h** actions.
- Reminders MUST survive device reboot (`BOOT_COMPLETED` rescheduling).
- Unused `SCHEDULE_EXACT_ALARM` MUST be removed unless exact timing is adopted.

**AC**
1. Setting a due date for tomorrow schedules a notification visible in
   `WorkManager` inspection; deleting the task cancels it.
2. Reboot then wait: the reminder still fires.
3. Denying the notification permission degrades gracefully with an in-app hint.

---

### FR-T2 — Overdue and daily agenda digest
**Requirements**
- A once-daily summary notification (opt-in, default off, user-set time) MUST
  report today's due count, overdue count and total LOE.
- Tapping it MUST open the list pre-filtered to Today + Overdue.

**AC** Enabling the digest at 08:00 produces exactly one notification per day
with accurate counts.

---

### FR-N1 — Full localization and accessibility conformance
**Requirements**
- All user-visible strings MUST come from `strings.xml`; quantities MUST use
  plurals; dates MUST use locale-aware formatting.
- The UI MUST pass an RTL pseudolocale review.
- All interactive targets MUST be ≥ 48×48 dp.
- Task cards MUST expose merged, meaningful TalkBack semantics
  ("Buy groceries, high priority, overdue by 3 days, not completed") plus custom
  actions for complete/delete.
- Colour MUST NOT be the sole carrier of meaning (overdue, priority, done).
- The app MUST support display scaling to 200% font size without clipping.
- Content descriptions MUST exist for every icon-only control.

**AC**
1. Accessibility Scanner reports zero critical issues on list, detail, settings.
2. Switching to `ar-XB` shows mirrored layout with no truncation.

---

### FR-P1 — Credential and data-at-rest security
**Requirements**
- The PAT MUST be stored encrypted (Keystore-backed).
- Existing plaintext tokens MUST be migrated transparently on first launch and
  the plaintext copy removed.
- `android:allowBackup` MUST be `false` or backup rules MUST exclude the
  settings store.
- The token MUST never be logged and MUST be masked in the UI by default.
- Only HTTPS remotes MUST be accepted (validated at entry, with a clear error).
- The app SHOULD support SSH keys or GitHub device-flow OAuth as an alternative
  to a PAT (MAY slip to R4).

**AC**
1. `adb backup` / device transfer does not carry the token.
2. Upgrading from the previous version keeps sync working with no re-entry.

---

## 6. R3 — Power

### FR-B1 — Bulk actions
**Requirements**
- Over a multi-selection the user MUST be able to: complete/uncomplete, delete,
  set priority, set due date, add/remove tags, move to project, set LOE.
- A bulk operation MUST produce **exactly one** commit and one push.
- Progress MUST be shown for operations over 20 items and MUST be cancellable.
- Bulk delete MUST honour trash + undo (FR-D2).
- Partial failure MUST report which items failed and leave the rest applied.

**AC** Bulk-completing 5 tasks yields one commit containing 5 file changes.

---

### FR-C1 — Fast capture (widget, shortcut, share)
**Requirements**
- A home-screen **Glance widget** MUST offer quick-add plus a compact list of
  today's tasks with inline complete.
- A launcher **App Shortcut** MUST open a quick-add sheet directly.
- The app MUST register a **share target** so text shared from any app becomes a
  new task (shared URL/text goes into the notes body).
- Quick-add MUST persist without opening the full editor and MUST work offline.

**AC**
1. Sharing a URL from a browser creates a task whose notes contain the URL.
2. Widget quick-add creates a file in `Tasks/` and refreshes the widget.

---

### FR-C2 — Natural-language quick entry
**Requirements**
- The quick-add field MUST parse inline syntax: `tomorrow`, `next friday`,
  `in 3 days`, `every monday` → due date/recurrence; `!high` → priority;
  `#tag` → tag; `@location` → location; `+project` → project; `~5` → LOE.
- Parsed tokens MUST be visually confirmed as chips before save and MUST be
  removable individually.
- Unparsed text MUST remain the title verbatim.
- Parsing MUST be locale-aware for date words and MUST be disable-able.

**AC** `Pay rent tomorrow !urgent #bills +Home ~2` creates a task titled
"Pay rent" with tomorrow's due date, urgent priority, tag `bills`, project
`Home`, LOE 2.

---

### FR-V1 — Agenda / calendar views
**Requirements**
- A **Today** view MUST show overdue + today's tasks with total LOE against the
  configured daily capacity, rendered as a capacity bar (green/amber/red).
- A **Week** view MUST show a 7-day agenda with per-day LOE totals and MUST
  support drag-to-reschedule between days.
- A **Month** calendar view SHOULD show due-date density and open a day's tasks.
- Daily capacity MUST be configurable in Settings (default 21 points).
- Over-capacity days MUST be visually flagged with a suggestion to reschedule.

**AC** Dragging a task from Wednesday to Friday rewrites `due:` and both days'
LOE totals update.

---

### FR-V2 — Insight and review
**Requirements**
- A stats screen SHOULD show completion counts (7/30/90 days), completed LOE
  velocity, overdue trend, and per-project distribution.
- Data MUST be derived from vault files only (no analytics upload, no network).
- A weekly review flow SHOULD walk overdue tasks and offer bulk reschedule.

**AC** Stats match a manual count of `status: done` files with `updated:` in range.

---

## 7. R4 — Reach

### FR-X1 — Multi-vault support
- Users MUST be able to configure multiple vaults (name, remote, credentials,
  local path) and switch between them from the drawer.
- Each vault MUST keep independent settings, filter state, and sync status.
- Switching vaults MUST NOT require re-cloning if the local copy exists.
- Reminders MUST remain scoped to the vault that owns the task.

### FR-X2 — Obsidian ecosystem interoperability
- `[[Wikilink]]` in notes MUST render as a tappable link with autocomplete over
  vault note names.
- Inline `#tags` typed in the body SHOULD be recognized as task tags.
- The app SHOULD read and preserve unknown frontmatter keys written by other
  Obsidian plugins (round-trip without loss) — this is a hard requirement for
  INV-2 and SHOULD be pulled forward to R1 if effort allows.
- The app SHOULD support the Obsidian Tasks plugin emoji date syntax
  (`📅 2026-08-01`) as an alternative source of due dates on read.

### FR-X3 — Attachments and rich notes
- Tasks SHOULD support attaching images/files stored under `Attachments/` and
  referenced with standard markdown embeds.
- The notes editor SHOULD offer a markdown preview toggle.

### FR-A1 — Template and default policies
- Users SHOULD be able to define task templates (default priority, tags,
  project, checklist skeleton) and apply one at creation.
- Per-project defaults SHOULD be supported.

---

## 8. Cross-cutting non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 | Cold start to interactive list ≤ 1.5 s on a mid-tier device with a 500-task vault (cached; no network on the critical path). |
| NFR-2 | List scroll ≥ 55 fps at 1,000 tasks; no frame over 16 ms in the 95th percentile. |
| NFR-3 | Any user action reflects in the UI within 100 ms; network work is always background. |
| NFR-4 | No task data is ever lost by an app-initiated operation; every destructive action is reversible for ≥ 8 s (undo) or ≥ 30 days (trash). |
| NFR-5 | No user data leaves the device except to the user's own configured git remote over HTTPS. No analytics, no telemetry, no third-party SDKs. |
| NFR-6 | `targetSdk` tracks the current Play requirement; release builds enable R8 + resource shrinking and ship a baseline profile. |
| NFR-7 | Unit-test coverage ≥ 70% for `data/` and `viewmodel/`; every frontmatter field has a round-trip test including adversarial values (colons, dashes, unicode, 200-char titles, newlines). |
| NFR-8 | Compose UI tests cover list rendering, filtering, create/edit/delete, and multi-select. CI runs unit tests, lint, and `assembleDebug` on every PR. |
| NFR-9 | Vault files written by the app open cleanly in Obsidian desktop with no plugin errors. |
| NFR-10 | The app functions fully with no remote configured (local-only mode) and never surfaces git errors in that mode. |

---

## 9. Out of scope (explicitly)

- Cloud sync services other than git (Dropbox, iCloud, proprietary backends).
- Server-side/hosted accounts or any SilverMeme-operated service.
- Real-time collaborative editing.
- AI/LLM task generation or summarization.
- iOS / desktop clients (Obsidian already covers desktop).

---

## 10. Open questions

1. **Merge strategy** — merge commits or rebase for FR-S1? Rebase keeps vault
   history readable; merge is safer with JGit's conflict reporting.
2. **Conflict granularity** — resolve per file, or per frontmatter field?
   Field-level is friendlier but requires a 3-way frontmatter merge.
3. **Reminder storage** — persist per-task reminder offsets in frontmatter
   (portable, visible to Obsidian) or in DataStore (invisible, device-local)?
4. **LOE capacity** — is 21 points/day the right default, and should capacity be
   per-day-of-week configurable?
5. **Trash location** — `Tasks/.trash/` is hidden from Obsidian by default;
   confirm this is desirable versus a visible `Archive/` folder.
6. **Scale ceiling** — is a 1,000-task target sufficient, or should the file
   cache be backed by a Room index for 10,000+?
