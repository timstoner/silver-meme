# SilverMeme — Remaining Feature Roadmap

**Updated:** 2026-09-27

This roadmap lists features that are not complete in the current app. The
requirements and acceptance criteria in `FEATURE_REQUIREMENTS.md` define the
expected behavior.

## Current baseline

The app stores tasks in Markdown files under `Tasks/`. It supports nested task
folders, project filters, checklists, recurring tasks, bulk task actions, soft
delete and restore, reminder scheduling, conflict resolution, and retry after
network recovery.

The app also has project records, a customizable dashboard, a quick-create
widget, and an agenda widget.

## Phase 1 — Project management **complete**

Project records are Obsidian-compatible Markdown files under `Projects/`.
Their stable IDs survive nested rename and move operations. Project lifecycle
status is user-managed and independent of task completion. Archived project
metadata and task folders move to the dedicated `Projects/.archive/` and
`Tasks/.archive/` locations; restoring returns them without changing task
content or identifiers. Unlimited project nesting is supported.

The Projects drawer destination provides search, status filters, an unassigned
task count, project health/count summaries, creation and editing, detail
metrics, due-date task groups, default task values, rename/move confirmation,
archive/restore, and bulk task moves. Local-only operations work without a Git
remote; configured remotes receive one automatic synchronization per complete
project operation.

## Phase 2 — Customizable dashboard **complete**

The dashboard now has interactive metrics, Today and project-health sections,
quick actions, a phone/tablet layout, and a persisted per-vault editor for
section visibility, order, density, project/time filters, and presets.

- [x] Wire dashboard metric/task and project navigation, quick actions, and
  dashboard presentation sections. The Today metric and “View today’s tasks”
  preserve the user's other task filters while adding a persisted
  incomplete-overdue-or-due-today constraint. Today task rows open individual
  task detail and label overdue versus due-today status in text.
- [x] Provide phone one-column and tablet adaptive-grid dashboard presentation.
- [x] Provide accessible dashboard editor controls for visibility, order, size,
  project/time filters, reset, and supplied presets.
- [x] Save layouts per vault and provide default/preset layout data.
- [x] Render the model-provided dynamic project-filter display name, including
  projects that have no project-health entry.
- [x] Render the last successful sync timestamp in text when it is available.
- [x] Describe stale sync state in text (not color alone) and let the user
  select a persisted stale-sync threshold in Settings.
- Keep cached data visible while sync runs.

All first-release dashboard requirements listed above are delivered. The stale
state is calculated from the persisted threshold and last successful sync time;
the UI states that condition in text as well as using the error color.

## Phase 3 — Daily workflow improvements

The task list supports selection and bulk actions. The task rows do not support
swipe actions. The quick-create widget opens the task editor, but the app has
no in-app quick-capture flow or saved views.

Implement the remaining workflow requirements:

- Add swipe-to-complete and swipe-to-trash, with accessible alternatives.
- Add quick capture with minimal required fields and later editing.
- Add saved views for common filters and sorts.
- Add daily planning actions and navigation continuity.
- Add the remaining search and bulk task actions from
  `FEATURE_REQUIREMENTS.md`.

**Acceptance:** Gesture actions have equivalent visible controls. Quick capture
creates a valid Markdown task. Saved views persist and restore their filters.

## Phase 4 — Vault scale and advanced features

Build these features after the project and dashboard data models stabilize:

- Add optional periodic background sync with a setting that is off by default.
  Network-recovery retry for pending changes already exists.
- Add multi-vault support, including vault selection and per-vault settings.
- Add incremental vault indexing and detection of external file changes.
- Add project board and calendar views.
- Add Obsidian wikilink suggestions for task notes.

**Acceptance:** Sync, project views, and settings use the selected vault.
External Markdown edits appear without data loss. Wikilinks remain valid
Obsidian Markdown.

## Implementation order

1. Resolve the project and dashboard decisions in Section 14 of
   `FEATURE_REQUIREMENTS.md`.
2. Implement Phase 1 and its storage and repository tests.
3. Implement Phase 2 and its dashboard state and UI tests.
4. Implement Phase 3 as independent task-list and quick-capture changes.
5. Design multi-vault storage before starting Phase 4.

## Completion checks

For each feature:

- Meet the acceptance criteria in this roadmap and
  `FEATURE_REQUIREMENTS.md`.
- Add tests at the storage, repository, ViewModel, or UI layer that owns it.
- Keep phone and tablet behavior consistent.
- Keep visible text in string resources.
- Preserve local-only use and Markdown compatibility.
- Update this roadmap when the feature is complete.
