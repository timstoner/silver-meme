# SilverMeme Product Requirements

**Version:** 2.0

**Date:** 2026-09-27

**Status:** Proposed

**Scope:** Project management, dashboard customization, and workflow efficiency

## 1. Purpose

SilverMeme manages tasks as Markdown files in an Obsidian vault. Git can synchronize the vault across devices.

This document defines the next product phase. The phase adds project management, a customizable dashboard, and faster daily workflows.

The requirements preserve two product rules:

- Markdown files remain the system of record.
- Users can edit the vault outside SilverMeme without data loss.

## 2. Review summary

The review covered the running tablet application, the Compose screens, navigation, the task model, and the ViewModel behavior.

### 2.1 Current strengths

- The application has a clear task model with priority, due date, tags, recurrence, location, checklist, and effort.
- Tasks support nested folders under `Tasks/`.
- The task list supports search, filters, sorting, due-date groups, bulk actions, trash, and Git synchronization.
- The tablet layout uses a task list and a detail pane.
- The application supports reminders, widgets, deep links, and light or dark themes.
- The architecture keeps Markdown storage, Git operations, presentation state, and Compose screens separate.

### 2.2 Current product gaps

| Area | Current behavior | Product gap |
|---|---|---|
| Projects | A project is a folder name derived from a task path. | Users cannot create, rename, archive, plan, or review a project as one object. |
| Project navigation | Project names appear only inside the filter panel. | Users cannot browse projects from the main navigation. |
| Project insight | The application can calculate task counts and effort. | It does not show progress, workload, deadlines, or project health. |
| Dashboard | The dashboard shows four full-width count cards. | Cards are not interactive, configurable, compact, or project-aware. |
| Dashboard layout | The tablet dashboard uses one wide column. | It does not use the available width for useful information. |
| Saved workflows | Filter state persists as one current state. | Users cannot save named views for repeated work. |
| Daily planning | Tasks are grouped by due date. | Users cannot plan capacity or reschedule work from one place. |
| Sync visibility | Sync feedback is mostly transient. | Users cannot see last sync time, pending work, or vault health on the dashboard. |
| Large vaults | The repository reloads the task collection after many changes. | Repeated file reads can reduce speed as the vault grows. |

## 3. Product goals

### G1. Manage work by project

Users can create a project, define its outcome, organize tasks, and monitor progress.

### G2. Make the dashboard useful

Users can choose the information, actions, order, and density of dashboard sections.

### G3. Reduce daily task-management effort

Common actions require fewer taps, fewer repeated filters, and fewer Git operations.

### G4. Preserve vault ownership

All project and dashboard data remains portable, readable, and safe in the user's vault or local settings.

## 4. Non-goals

This phase does not add these capabilities:

- Real-time collaboration
- A SilverMeme cloud account
- Chat, comments, or team permissions
- Gantt charts with dependency scheduling
- Billing, time tracking, or invoicing
- Automatic task creation by an external AI service
- A replacement for Obsidian or Git

## 5. Users and primary workflows

### 5.1 Individual planner

This user manages personal, home, and work projects in one vault.

Primary needs:

- See the most important work at startup.
- Open one project without constructing a filter.
- Capture a task into the correct project.
- Find stalled or overdue projects.
- Keep the same workflow on a phone and tablet.

### 5.2 Obsidian user

This user edits the same task files on a desktop and Android device.

Primary needs:

- Keep project data readable in Obsidian.
- Preserve unknown frontmatter and body content.
- Move or rename projects without losing task history.
- Understand which changes are local, pending, or synchronized.

### 5.3 High-volume task user

This user manages hundreds or thousands of task files.

Primary needs:

- Open the application without waiting for a network operation.
- Search and filter without visible delay.
- Apply one action to many tasks.
- Avoid one Git commit for each item in a bulk change.

## 6. Information model

### 6.1 Task

The existing `TodoItem` remains the unit of work. Its vault-relative file path continues to determine project membership.

### 6.2 Project

A project is a named container for related tasks. A project maps to one folder under `Tasks/`.

A project record MUST support these fields:

| Field | Requirement |
|---|---|
| Identifier | The identifier MUST remain stable after a project rename. |
| Name | The name MUST be unique within its parent project. |
| Path | The path MUST support nested projects. |
| Status | The status MUST be `active`, `on-hold`, `completed`, or `archived`. |
| Description | The project CAN contain a Markdown description. |
| Target date | The project CAN contain a target completion date. |
| Color | The project CAN contain a user-selected display color. |
| Icon | The project CAN contain a user-selected icon. |
| Default values | The project CAN define default priority, tags, effort, and recurrence. |
| Created and updated dates | The project MUST record creation and update dates. |

Project metadata MUST use an Obsidian-compatible Markdown format. The final file location requires a product decision in Section 14.

### 6.3 Dashboard layout

Dashboard layout data is user-interface state. It MUST remain separate from task content.

The layout MUST be stored per vault. It MUST include section visibility, order, size, filters, and time range.

## 7. Project management requirements

### PRJ-1. Project hub

The navigation drawer MUST contain a Projects destination.

The project hub MUST show:

- Active projects
- On-hold projects
- Completed projects
- Archived projects
- Unassigned tasks

Each project row or card MUST show:

- Project name and icon
- Open task count
- Completed task count
- Overdue task count
- Total remaining effort
- Nearest task due date
- Project target date, when present

The project hub MUST support search and status filters.

**Acceptance criteria**

1. A user can open any active project in two actions from the dashboard.
2. The project counts match the tasks in the project folder.
3. A project with no tasks remains visible when it has a project record.

### PRJ-2. Create a project

Users MUST be able to create a project without first creating a task.

The creation flow MUST request a name. All other fields MUST be optional.

The application MUST reject these invalid states:

- An empty name
- A duplicate path
- A path that escapes the `Tasks/` folder
- A name that conflicts with a task file path

Project creation MUST work without a Git remote.

**Acceptance criteria**

1. A new project appears in the project hub before it contains tasks.
2. A new task can select the project immediately.
3. The project remains available after application restart.

### PRJ-3. Project detail

The project detail screen MUST combine planning information and project tasks.

The screen MUST show:

- Project description
- Project status
- Project target date
- Progress by completed task count
- Progress by completed effort
- Open, overdue, and completed counts
- Remaining effort
- Upcoming due dates
- Tasks grouped by due-date status

The screen MUST provide these actions:

- Add a task to this project
- Edit project details
- Rename the project
- Move the project
- Change project status
- Archive the project
- Open the project folder in a filtered task list

Dashboard and project metrics MUST exclude trashed tasks.

### PRJ-4. Rename and move a project

A project rename MUST move its task folder and preserve all nested paths.

The operation MUST preserve:

- Task identifiers
- Task content
- Task history in Git
- Nested subprojects
- Trash restore paths
- Reminder ownership
- Dashboard references
- Saved-view references

The application MUST show the number of affected tasks before the move.

The application MUST use one Git commit and one push for the complete move.

If one file cannot move, the application MUST report the file. It MUST not report complete success.

### PRJ-5. Project status and archive

Users MUST be able to change a project status without changing every task.

Archived projects MUST remain in the vault. They MUST not appear in default active-project lists.

Archiving a project MUST not complete or remove its open tasks.

The archive action MUST warn the user when the project contains open tasks.

Users MUST be able to restore an archived project.

### PRJ-6. Project defaults

A project CAN define default values for new tasks.

Supported defaults:

- Priority
- Tags
- Level of effort
- Recurrence
- Reminder behavior

The task editor MUST show all applied defaults. The user MUST be able to change each value before saving.

Project defaults MUST not change existing tasks.

### PRJ-7. Project views

Each project MUST support these views:

- List
- Due-date groups
- Board by task status
- Calendar or agenda

The first release MUST include the list and due-date views.

The board and calendar views are later requirements. They MUST use the same task files and project filters.

### PRJ-8. Cross-project task movement

Users MUST be able to move one task or many tasks to another project.

A move MUST preserve the task identifier and all task fields.

A bulk move MUST use one Git commit and one push.

The move flow MUST support a new project as the destination.

## 8. Dashboard customization requirements

### DSH-1. Interactive dashboard

Every dashboard metric MUST open the related task or project view.

For example:

- Open tasks opens the active task list.
- Due today opens the Today view.
- Overdue opens the overdue task list.
- Completed opens the completed task list.

The dashboard MUST not use non-interactive cards for navigational data.

### DSH-2. Dashboard sections

The dashboard MUST support these sections:

| Section | Content |
|---|---|
| Today | Overdue tasks and tasks due today |
| Upcoming | Tasks due during the selected time range |
| Project health | Active projects with progress, overdue work, and target-date risk |
| Workload | Effort by day and capacity use |
| Quick actions | New task, new project, sync, and saved views |
| Task summary | Open, due today, overdue, and completed counts |
| Recent activity | Recently created, changed, completed, or restored tasks |
| Sync status | Last successful sync, pending changes, conflicts, and retry action |
| Focus | User-selected tasks or one selected project |

The first release MUST include Today, Project health, Quick actions, Task summary, and Sync status.

### DSH-3. Dashboard editor

Users MUST be able to customize the dashboard.

The editor MUST support these actions:

- Show or hide a section.
- Reorder sections.
- Select compact, standard, or expanded size.
- Select a project filter.
- Select a time range.
- Restore the default layout.

The application MUST provide a useful default layout for new users.

Dashboard customization MUST persist after restart. It MUST remain separate for each vault.

### DSH-4. Responsive layout

The phone dashboard MUST use one column.

The tablet dashboard MUST use an adaptive grid with two or three columns.

A large section CAN span more than one column. A summary metric MUST use compact space on a tablet.

The layout MUST support portrait and landscape orientations. It MUST support 200 percent font scaling without clipped controls.

### DSH-5. Project health

The project-health section MUST identify projects that need attention.

A project is at risk when one or more conditions apply:

- The project contains overdue tasks.
- The target date is near and significant effort remains.
- The project has no completed task during the selected period.
- The project has no next action.
- The project is on hold but still has due tasks.

The application MUST explain each risk label. Color MUST not be the only risk indicator.

The user MUST be able to change the target project or show all active projects.

### DSH-6. Dashboard presets

The application MUST provide these presets:

- Daily planning
- Project review
- Minimal

Users CAN save custom presets. A custom preset MUST include the complete section layout and filters.

### DSH-7. Dashboard freshness

The dashboard MUST show cached local data immediately.

A remote sync MUST not block the first dashboard render.

Metrics MUST update after a local task or project change.

The sync-status section MUST show stale data when the last successful sync exceeds a configurable threshold.

## 9. Workflow efficiency requirements

### EFF-1. Quick capture

The application MUST provide one quick-add field from the dashboard and task list.

The first step MUST require only a title.

The user CAN add a project, due date, priority, or tag before saving.

The application MUST remember the last selected project during one capture session.

A saved task MUST appear in the interface within 100 ms.

### EFF-2. Saved views

Users MUST be able to save the current task filters and sort order as a named view.

A saved view MUST include:

- Project
- Completion state
- Overdue state
- Priority
- Search query
- Sort order
- Due-date range

Saved views MUST appear in the navigation drawer and dashboard quick actions.

Users MUST be able to rename, reorder, and remove saved views.

### EFF-3. Bulk actions

Bulk actions MUST support:

- Complete or reopen
- Move to project
- Set priority
- Set due date
- Add or remove tags
- Set effort
- Move to trash

One bulk action MUST use one repository operation, one Git commit, and one push.

The interface MUST show progress for more than 20 tasks.

A partial error MUST list the affected tasks and preserve successful changes.

### EFF-4. Fast project planning

The project detail screen MUST support an inline task-add action.

The inline action MUST assign the current project automatically.

Users MUST be able to reorder undated tasks into a manual project order.

Users MUST be able to select several tasks and assign one due date or date range.

### EFF-5. Daily planning

The Today view MUST show overdue tasks before tasks due today.

The view MUST show total remaining effort and the configured daily capacity.

Users MUST be able to defer a task by one day, one week, or a selected date.

Users MUST be able to complete, defer, or move a task without opening the full editor.

### EFF-6. Search

Search MUST cover titles, notes, tags, project names, locations, and checklist text.

Search results MUST update within 200 ms for a vault with 1,000 tasks.

Search MUST support quoted phrases and field filters.

Initial field filters:

- `project:`
- `tag:`
- `priority:`
- `due:`
- `status:`

### EFF-7. Navigation continuity

The application MUST preserve the current project, saved view, scroll position, and selected task during navigation.

On tablets, the selected task MUST remain visible after a local update.

The tablet layout MUST use the detail pane for project and dashboard drill-downs when practical.

### EFF-8. Synchronization efficiency

The application MUST combine related changes into one synchronization operation.

Automatic synchronization MUST be throttled. Repeated navigation MUST not start repeated pulls.

Local writes MUST succeed when the network is unavailable.

The interface MUST show these states:

- Synced with time
- Local changes pending
- Offline
- Synchronization error
- Conflict requires action

### EFF-9. File access efficiency

The application MUST avoid a complete vault read after each single-task change.

The application MUST update its in-memory state after a successful local write.

A background file scan MUST detect external Obsidian changes.

The design CAN add a local index. The Markdown vault MUST remain the system of record.

### EFF-10. Keyboard and large-screen efficiency

The application MUST support keyboard navigation on tablets and Chromebooks.

Required shortcuts:

- New task
- Search
- Open dashboard
- Open projects
- Synchronize
- Save
- Close or go back

Visible focus indicators MUST appear for keyboard navigation.

## 10. Cross-cutting requirements

### 10.1 Data integrity

- The application MUST preserve unknown frontmatter keys during an edit.
- A project operation MUST not erase task content.
- Destructive actions MUST use trash or an undo period.
- Project folder changes MUST prevent path traversal.
- Each schema change MUST include round-trip coverage.

### 10.2 Obsidian compatibility

- Task files MUST remain valid Markdown files.
- Task metadata MUST remain valid YAML frontmatter.
- Checklist items MUST remain standard Markdown checklist lines.
- Project metadata MUST remain readable without SilverMeme.
- Nested project folders MUST remain usable in Obsidian.

### 10.3 Accessibility

- Every interactive control MUST have a non-gesture action.
- Touch targets MUST be at least 48 by 48 dp.
- Screen-reader labels MUST include state and purpose.
- Dashboard cards MUST expose headings and ordered navigation.
- Project health MUST not depend only on color.
- All screens MUST support 200 percent font scaling.

### 10.4 Localization

- All user-visible text MUST use string resources.
- Dates, times, numbers, and plural forms MUST use the current locale.
- Project names and task content MUST preserve Unicode text.
- Layouts MUST support right-to-left languages.

### 10.5 Privacy and security

- No analytics data can leave the device.
- No task or project content can leave the configured Git remote.
- Credentials MUST use encrypted storage.
- Logs MUST not contain tokens, task notes, or project descriptions.
- Remote URLs MUST use HTTPS unless a future approved transport replaces it.

## 11. Performance and efficiency targets

| ID | Target |
|---|---|
| NFR-1 | The cached dashboard becomes interactive within 1.5 seconds on a mid-tier device. |
| NFR-2 | The cached task list becomes interactive within 1.5 seconds for 1,000 tasks. |
| NFR-3 | A local task action appears in the interface within 100 ms. |
| NFR-4 | Search results update within 200 ms for 1,000 tasks. |
| NFR-5 | List scrolling maintains at least 55 frames per second for 1,000 tasks. |
| NFR-6 | A dashboard update does not start a full vault scan after one local task change. |
| NFR-7 | A bulk change creates one Git commit and one push. |
| NFR-8 | Opening the dashboard does not require a network connection. |
| NFR-9 | Automatic synchronization starts no more than once per 60 seconds without a user action. |
| NFR-10 | Project rename handles 1,000 task files with progress feedback and no data loss. |
| NFR-11 | Background work respects Android battery and network constraints. |
| NFR-12 | The application remains usable with no Git remote. |

## 12. Product measures

The product can measure these values locally. No analytics upload is required.

| Measure | Target |
|---|---|
| Task capture steps | A basic task requires two actions after the quick-add field opens. |
| Project access | An active project requires no more than two actions from the dashboard. |
| Daily planning | A user can review and adjust today's tasks from one screen. |
| Dashboard usefulness | Every visible dashboard section provides information or a direct action. |
| Bulk efficiency | One action can change at least 100 selected tasks. |
| Sync clarity | Every unsynchronized change has a visible status. |
| Project clarity | Every active project shows progress and its next due work. |

## 13. Delivery plan

### Phase 1. Project foundation

This phase includes:

- Project records
- Project hub
- Project detail
- Project create, edit, rename, move, archive, and restore
- Bulk task movement
- Project-aware navigation

### Phase 2. Dashboard foundation

This phase includes:

- Interactive dashboard cards
- Adaptive tablet grid
- Today section
- Project-health section
- Quick actions
- Sync-status section
- Dashboard editor

### Phase 3. Workflow efficiency

This phase includes:

- Saved views
- Quick capture
- Daily planning actions
- Expanded bulk actions
- Search field filters
- Navigation continuity

### Phase 4. Scale and advanced views

This phase includes:

- Incremental vault index
- External file-change detection
- Project board
- Project calendar
- Dashboard custom presets
- Keyboard shortcuts

## 14. Open product decisions

1. **Project metadata location:** Use a metadata file inside each task folder, or use a separate `Projects/` folder.
2. **Project completion:** Calculate completion from tasks, or let the user set project status independently.
3. **Manual task order:** Store order in project metadata, or add an optional task frontmatter field.
4. **Dashboard storage:** Keep dashboard layouts only on the device, or synchronize them through the vault.
5. **Risk rules:** Use fixed project-health rules, or let users configure thresholds.
6. **Archive behavior:** Keep archived projects under `Tasks/`, or move them to a dedicated archive folder.
7. **Capacity:** Use one daily effort limit, or support different limits for each weekday.
8. **Project hierarchy:** Support unlimited nesting, or set a supported depth.

## 15. Definition of done

A feature is complete when all applicable conditions are true:

- The acceptance criteria pass.
- Phone and tablet layouts provide the same capability.
- Accessibility actions exist for gesture-based behavior.
- User-visible text uses string resources.
- Markdown and project data survive a save and reload.
- Local-only mode remains functional.
- Git synchronization reports all errors.
- The feature has unit or interface coverage at the correct layer.
- The documentation describes the final behavior.
