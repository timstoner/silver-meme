# SilverMeme Implementation Plan - Phase 2

**Date:** July 31, 2026  
**Status:** Planning (No implementation started)  
**Priority:** High for UX/Polish improvements

---

## Table of Contents

1. [Feature Overview](#feature-overview)
2. [Detailed Feature Specifications](#detailed-feature-specifications)
3. [Implementation Phases](#implementation-phases)
4. [Architecture Changes](#architecture-changes)
5. [UI/UX Changes](#uiux-changes)
6. [Data Persistence](#data-persistence)
7. [Testing Strategy](#testing-strategy)
8. [Dependencies](#dependencies)

---

## Feature Overview

This document outlines enhancements to SilverMeme to improve usability, visual hierarchy, and organization:

| Feature | Priority | Impact | Complexity |
|---------|----------|--------|-----------|
| Dark Theme + Theme Switching | High | UX | Medium |
| Overdue Indicator | High | UX | Low |
| Group Items by Due Date | High | UX | Medium |
| Schedule UI for Recurring Todos | High | Feature | Medium |
| Priority Label Positioning | Low | UX | Low |
| Persist Filter State | High | UX | Medium |
| Save ViewModel State | High | Performance | Medium |
| Navigation Drawer | High | UX | High |
| Folders/Projects UI | Medium | Feature | High |
| **Level of Effort (LOE)** | **High** | **Feature** | **Medium** |

---

## Detailed Feature Specifications

### 1. Dark Theme + Theme Switching

**Goal:** Support both light and dark themes with system preference detection and manual override.

**Data Model Changes:**
```
SettingsDataStore additions:
  - themeMode: Flow<String> (values: "system", "light", "dark")
  - accentColor: Flow<String> (hex color code, default: system blue)
```

**UI Components:**
- Create `AppTheme.kt` composable wrapper with `isSystemInDarkTheme()` detection
- Define color palettes:
  - **Light:** Primary blue, neutral grays (white bg)
  - **Dark:** Darker blue, elevated surface colors (dark gray bg)
- Apply Material 3 `dynamicLightColorScheme()` / `dynamicDarkColorScheme()` if available

**Screens/Navigation:**
- Add Settings screen section: "Theme" with RadioButton group:
  - "System Default" (auto-switch based on system setting)
  - "Light Mode" (force light)
  - "Dark Mode" (force dark)
- Live preview of colors when changing selection
- Persist selection to DataStore

**Architecture:**
- Create `ThemePreferences` data class
- Add `getThemeMode()` to SettingsDataStore
- Pass `isDarkMode` boolean down Compose hierarchy via theme parameter
- Update all color references to use theme-aware palette

**Files to Create/Modify:**
- `ui/theme/AppTheme.kt` (NEW)
- `ui/theme/Colors.kt` (NEW) - light/dark palettes
- `ui/screens/SettingsScreen.kt` (MODIFY - add theme selector)
- `data/preferences/SettingsDataStore.kt` (MODIFY - add themeMode Flow)
- `SilverMemeApplication.kt` (MODIFY - inject theme preference)

---

### 2. Overdue Indicator

**Goal:** Visually highlight tasks that are past their due date.

**Design:**
- Red/warning badge or border on `TodoItemCard` when `dueDate < today` and `isCompleted == false`
- Icon indicator (⚠️ or ⏰ with strikethrough) next to due date
- Optional: Red text for due date field itself
- Combine with filtering: ability to show/hide overdue tasks

**Logic:**
```
isOverdue = (dueDate != null && dueDate < LocalDate.now() && !isCompleted)
```

**UI Elements:**
- `TodoItemCard.kt`: Add conditional border/background tint when overdue
- Add overdue icon before due date display
- Consider subtle shadow or elevation change to draw attention

**Modification Files:**
- `ui/components/TodoItemCard.kt` (MODIFY)
- `viewmodel/TodoViewModel.kt` (MODIFY - add `fun isOverdue(todo): Boolean`)

**Interaction:**
- Show/hide overdue filter in list screen
- "Show Overdue" toggle already exists in FilterState, ensure it's wired to UI

---

### 3. Group Items by Due Date

**Goal:** Organize task list into semantic groups: "Overdue", "Today", "Tomorrow", "This Week", "Next Week", "Later", "No Due Date".

**Data Structure:**
```kotlin
data class TodoGroup(
    val label: String,  // e.g., "Today", "Tomorrow"
    val dueDate: LocalDate?,  // representative date for this group
    val todos: List<TodoItem>
)

// In TodoViewModel:
fun groupedTodos(): List<TodoGroup>
```

**Grouping Logic:**
- **Overdue:** dueDate < today, !isCompleted
- **Today:** dueDate == today
- **Tomorrow:** dueDate == today + 1 day
- **This Week:** dueDate in (today+2 days) to (today+7 days)
- **Next Week:** dueDate in (today+8 days) to (today+14 days)
- **Later:** dueDate > today+14 days
- **No Due Date:** dueDate == null

**Order:** Overdue → Today → Tomorrow → This Week → Next Week → Later → No Due Date

**Filters:**
- Respect existing FilterState (priority, search, completed, recurrence)
- Apply grouping AFTER filtering
- Option to collapse/expand groups

**UI Implementation:**
- `TodoListScreen.kt`: Replace flat list with grouped list
- Add section headers with group label and task count
- Add expand/collapse toggle per group (optional)
- Sticky headers as user scrolls (optional enhancement)

**Modification Files:**
- `viewmodel/TodoViewModel.kt` (MODIFY - add groupedTodos() function)
- `ui/screens/TodoListScreen.kt` (MODIFY - refactor to use grouped layout)
- `ui/components/TodoListSection.kt` (NEW - reusable group header + items)

**Stretch Goal:**
- Remember collapsed/expanded state per group in DataStore

---

### 4. Schedule UI for Recurring Todos

**Goal:** Provide UI to set/edit recurrence pattern and visualize next occurrence.

**Current State:**
- Recurrence field exists in data model: "daily", "weekly", "monthly", "none"
- toggleComplete() spawns next occurrence automatically
- NO UI to set/change recurrence yet

**New UI Components:**
- In `TodoDetailScreen`: Add "Recurrence" section:
  - Spinner/Dropdown with options: None, Daily, Weekly, Monthly
  - Display of next occurrence (if recurrence != "none"):
    - "Next task due: September 5, 2026"
  - Visual indicator: "🔁 Repeats [pattern]"

**Edit Workflow:**
1. User opens task detail
2. Taps "Recurrence" dropdown
3. Selects: None / Daily / Weekly / Monthly
4. Save (updates frontmatter)
5. If currently showing future occurrence, show "Updated" toast
6. Navigation back to list (list auto-refreshes)

**Smart Logic:**
- Warn if user tries to set recurrence on task with no due date
- When recurrence is set to "none", keep existing task; don't spawn anything
- When recurrence is changed (e.g., weekly → daily), update next spawn date

**Modification Files:**
- `ui/screens/TodoDetailScreen.kt` (MODIFY - add recurrence section)
- `ui/components/RecurrenceSelector.kt` (NEW - reusable dropdown + preview)
- `viewmodel/TodoViewModel.kt` (MODIFY - add updateRecurrence() method)

**Data Persistence:**
- Recurrence already persists in markdown frontmatter (Track E2)
- No additional storage needed

---

### 5. Priority Label Positioning

**Goal:** Move priority label from start of row to end (right-aligned).

**Current State:**
- `TodoItemCard` shows: `[Priority Badge] Title [Due Date] [Status]`

**Desired State:**
- `TodoItemCard` shows: `Title [Due Date] [Status] [Priority Badge]`

**Design Rationale:**
- Title is most important, should be leftmost
- Priority is secondary visual info, place at end
- Keeps layout right-aligned with due date/status

**UI Changes:**
- Modify `TodoItemCard` Row layout
- Swap priority badge position (from start to end)
- Adjust spacer/padding accordingly
- Ensure accessibility (screen readers still announce priority)

**Modification Files:**
- `ui/components/TodoItemCard.kt` (MODIFY - reorder composables in Row)

**Complexity:** Very low (mostly layout adjustment)

---

### 6. Persist Filter State Between Screens

**Goal:** Remember user's active filters (priority, completed, overdue, search, sort) when navigating away and back to list.

**Current State:**
- FilterState exists in TodoViewModel
- No persistence; resets to defaults on screen close
- Multi-select state also not persisted

**Solution:**
- Add `FilterState` and `selectedIds` serialization to SettingsDataStore
- Write to DataStore whenever filter changes
- Restore from DataStore on TodoViewModel init

**Data Model:**
```kotlin
// In SettingsDataStore:
val lastFilterState: Flow<String> = dataStore.data.map { prefs ->
    prefs[stringPreferencesKey("filter_state")] ?: "{}"
}

val lastSelectedIds: Flow<String> = dataStore.data.map { prefs ->
    prefs[stringPreferencesKey("selected_ids")] ?: "[]"
}
```

**Serialization:**
- Use Kotlinx Serialization or Gson to convert FilterState/Set<String> to JSON
- Store as single string preference keys
- Deserialize on app launch and restore to ViewModel

**Implementation:**
1. Create `FilterStateSerializer.kt` helper
2. Update TodoViewModel to:
   - Load saved filter state in init{}
   - Save to DataStore whenever FilterState changes
3. Update SettingsDataStore to include filter persistence
4. Add toggle in Settings: "Remember Filters" (optional disable)

**Modification Files:**
- `data/preferences/SettingsDataStore.kt` (MODIFY)
- `viewmodel/TodoViewModel.kt` (MODIFY - init, _filterState updates)
- `util/FilterStateSerializer.kt` (NEW)

**Edge Cases:**
- Handle corrupted JSON gracefully (fall back to defaults)
- Clear filters on explicit "Reset" action in UI
- Consider screen rotation: restore on config change

---

### 7. Save ViewModel State to Preferences

**Goal:** Persist ViewModel UI state (todos list, loading flags, error messages) to enable faster app restart.

**Current State:**
- ViewModel loads todos on init via `loadTodos()` (repository call)
- Every cold start fetches all .md files from vault
- 1-2 second delay before list appears

**Optimization:**
- Cache `TodoUiState.todos` to local preference on every change
- On app restart, load from cache (instant display)
- Run `loadTodos()` in background to refresh (sync with vault/git)
- Show cached list immediately, update smoothly when fresh data arrives

**Implementation:**
```kotlin
// In TodoViewModel:
private suspend fun saveCachedState(state: TodoUiState) {
    // Serialize todos list to JSON
    // Store in SettingsDataStore
}

private suspend fun loadCachedState(): TodoUiState? {
    // Deserialize from SettingsDataStore
}

init {
    // Load from cache first (instant)
    val cached = loadCachedState()
    if (cached != null) {
        _uiState.value = cached
    }
    // Then refresh in background
    loadTodos()
}
```

**Data Persistence:**
- Use Kotlinx Serialization for TodoItem list
- Store in SettingsDataStore as JSON string
- Update cache whenever todos list changes (saveTodo, deleteTodo, toggleComplete)

**Cache Invalidation:**
- Cache is automatically refreshed on every loadTodos()
- Manual "Refresh" button (if added to UI) forces immediate refresh
- Consider TTL: if cache > 1 hour old, always refresh

**Modification Files:**
- `data/preferences/SettingsDataStore.kt` (MODIFY)
- `viewmodel/TodoViewModel.kt` (MODIFY)
- `util/TodoSerializers.kt` (NEW - Kotlinx Serialization helpers)

**Testing:**
- Verify cache is populated after first load
- Test app restart shows cached list
- Confirm refresh updates display with fresh data
- Test corrupted cache doesn't crash app

---

### 8. Navigation Drawer

**Goal:** Add a drawer-based filter sidebar for quick filtering by project, priority, due date, and completion status.

**Current State:**
- Filters exist in UI but scattered across Compose logic
- No dedicated filter panel/drawer

**Drawer Contents:**
```
┌─────────────────────────────┐
│ Filters                  [X] │
├─────────────────────────────┤
│ Projects                  ▼ │
│  ☐ All (count)              │
│  ☐ Personal (count)         │
│  ☐ Work (count)             │
│  ☐ Shopping (count)         │
│                             │
│ Priority                  ▼ │
│  ☐ All                      │
│  ☐ Low                      │
│  ☐ Medium                   │
│  ☐ High                     │
│  ☐ Urgent                   │
│                             │
│ Status                    ▼ │
│  ☐ Incomplete only          │
│  ☐ Completed only           │
│  ☐ Show overdue             │
│                             │
│ Recurring                 ▼ │
│  ☐ Recurring only           │
│  ☐ Non-recurring only       │
│                             │
│ [Reset Filters] [Apply]     │
└─────────────────────────────┘
```

**Interaction:**
- Drawer slides in from left edge
- Checkboxes for multi-select filters
- Real-time count badges show matching items per filter
- "Reset Filters" button clears all selections
- "Apply" button closes drawer and updates list
- Drawer closes on back press or item selection

**Implementation:**
- Use Jetpack Compose `DrawerState` and `ModalNavigationDrawer`
- Organize filters into expandable sections (collapsible headers)
- Real-time filtering: map FilterState selections to task counts
- Accessibility: ensure keyboard navigation works

**Data Structure:**
```kotlin
data class FilterOption(
    val id: String,
    val label: String,
    val count: Int,
    val isSelected: Boolean,
    val category: FilterCategory  // Project, Priority, Status, etc.
)

enum class FilterCategory {
    PROJECT, PRIORITY, STATUS, RECURRING
}
```

**Modification Files:**
- `ui/navigation/NavGraph.kt` (MODIFY - wrap scaffold with drawer)
- `ui/screens/TodoListScreen.kt` (MODIFY - extract drawer logic)
- `ui/components/FilterDrawer.kt` (NEW)
- `ui/components/FilterOptionItem.kt` (NEW)
- `viewmodel/TodoViewModel.kt` (MODIFY - add getAvailableProjects(), getFilterCounts())

**State Management:**
- Drawer state: open/closed (ModalNavigationDrawer state)
- Temporary filter state (user adjusts but hasn't applied)
- Apply button syncs temporary → active FilterState

---

### 9. Folders/Projects (UI Layer)

**Note:** Data model support exists (Track E3); this section covers UI/UX.

**Current State:**
- `TodoItem.project` derived field (computed from filePath)
- `MarkdownFileManager` supports recursive scanning
- FilterState includes project filter
- NO UI to view/manage projects yet

**UI Features:**

#### 9a. Project Column/Tag
- Show project label on TodoItemCard (e.g., "Work", "Personal")
- Optional color-coding per project (configurable in Settings)
- Click project tag to filter by that project

#### 9b. Project Management Screen
- New Settings tab: "Projects"
- Display list of all projects (with task counts)
- Add/Edit/Delete projects
- Rename project (moves all tasks in folder)
- Set project color/icon

#### 9c. Project-Based Navigation
- Drawer includes Projects section (see Navigation Drawer spec above)
- Clicking project name filters list by that project
- Breadcrumb: "Tasks / Work / (3 items)" at top of list

#### 9d. Task Creation - Project Assignment
- In TodoDetailScreen (create mode):
  - Add "Project" dropdown
  - User selects existing project or creates inline
  - Task is saved to `Tasks/ProjectName/` subfolder

**Data Persistence:**
- Projects are derived from folder structure
- No separate Projects table needed
- Color per project stored in SettingsDataStore:
  ```kotlin
  projectColors: Flow<String> = { "Work": "#FF5733", "Personal": "#33FF57" }
  ```

**Implementation Approach:**
1. Add `getAvailableProjects(): List<String>` to TodoRepository
2. Scan getAllTodos(), extract unique projects
3. Display in Settings and Drawer
4. Allow user to assign project when creating/editing task
5. Store project→color mapping in DataStore

**Modification Files:**
- `ui/screens/SettingsScreen.kt` (MODIFY - add Projects tab)
- `ui/screens/ProjectsManagementScreen.kt` (NEW)
- `ui/components/ProjectTag.kt` (NEW)
- `ui/components/FilterDrawer.kt` (MODIFY - add Projects section)
- `data/preferences/SettingsDataStore.kt` (MODIFY - project colors)
- `viewmodel/TodoViewModel.kt` (MODIFY - add getAvailableProjects())

---

### 10. Level of Effort (LOE) & Daily Planning

**Goal:** Allow users to estimate task complexity/effort using story point-style LOE values. Enable capacity planning for a day by summing LOE across tasks.

**Motivation:** 
- Helps users understand task load and commit to realistic daily goals
- Enables burndown tracking: "Can I complete all these tasks today? They total 13 LOE points."
- Aligns with agile estimation practices

**Data Model Changes:**
```kotlin
// In TodoItem.kt:
data class TodoItem(
    ...
    /** Level of Effort estimate (1-13, using Fibonacci scale for agile compatibility) */
    val loe: Int = 0  // 0 means "not estimated"
    ...
)
```

**LOE Scale (Fibonacci):**
- **0:** Not estimated (default)
- **1:** Trivial (< 15 min, e.g., "Send email")
- **2:** Quick (15-30 min, e.g., "Quick fix")
- **3:** Small (1-2 hours, e.g., "Add form field")
- **5:** Medium (2-4 hours, e.g., "Implement feature")
- **8:** Large (1 day, e.g., "Major refactor")
- **13:** Very Large (2+ days, e.g., "Complex feature")

**Display Format:**
- Show on TodoItemCard: `"LOE: 5"` or badge with number
- Position: Near priority badge (end of row)
- Optional: Color-code by LOE (1-2 green, 3-5 yellow, 8+ red)

**UI Components:**

#### 10a. LOE Selector in TodoDetailScreen
- Add "Level of Effort" dropdown in edit screen
- Options: "Not Estimated", "1 (Trivial)", "2 (Quick)", "3 (Small)", "5 (Medium)", "8 (Large)", "13 (Very Large)"
- Visual preview: "You've estimated this as ~[duration] of work"
- Tooltip: Explain each level

#### 10b. Daily Planner Widget / Screen
- New screen: "Plan Your Day" (accessible from settings or drawer)
- Show:
  - Date selector (default today)
  - "Today's Tasks" section with LOE column
  - Total LOE for today: `"📊 Total Effort: 13 points (2-3 hours)"`
  - Capacity indicator: Bar chart showing LOE vs. recommended daily capacity (e.g., 21 points = 8-hour day)
  - Available capacity: `"13 / 21 points used"`
  
**Daily Capacity Calculation:**
```
Recommended daily capacity = 21 LOE points (8-hour work day)
  - Breakdown: 1-2 hours light tasks, 2-4 hours medium, 1+ hour deep work
  - User can customize in Settings

Daily Load Indicator:
  - Green: 0-14 points (< 50% capacity, comfortable)
  - Yellow: 15-21 points (50-100% capacity, full day)
  - Red: 22+ points (> 100% capacity, overcommitted)
```

#### 10c. Filtering by LOE
- In Navigation Drawer: Add "Effort Level" filter section
- Options: Show only 1-2 (Quick), 3-5 (Medium), 8+ (Large), Unestimated
- Useful for: "What can I finish quickly today?"

#### 10d. LOE Statistics & Insights
- In Settings dashboard:
  - "Average LOE per task"
  - "Most estimated LOE value" (e.g., "You estimate most tasks as '3'")
  - "Unestimated tasks" count

**Persistence:**
- Store LOE in markdown frontmatter (new field)
  ```yaml
  ---
  id: task-1
  title: Buy groceries
  loe: 2
  ---
  ```

**Implementation Files:**
- `data/model/TodoItem.kt` (MODIFY - add loe field)
- `data/storage/MarkdownFileManager.kt` (MODIFY - parse/serialize loe)
- `ui/components/TodoItemCard.kt` (MODIFY - display LOE badge)
- `ui/components/LoeSelector.kt` (NEW - dropdown/radio group)
- `ui/screens/PlanYourDayScreen.kt` (NEW - daily planner)
- `ui/screens/TodoDetailScreen.kt` (MODIFY - add LOE editor)
- `ui/components/FilterDrawer.kt` (MODIFY - add LOE filter)
- `viewmodel/TodoViewModel.kt` (MODIFY - add getTodayLoe(), getLoeCapacityPercent())
- `data/preferences/SettingsDataStore.kt` (MODIFY - store daily capacity preference)

**Smart Logic:**
- Warn when user commits to tasks exceeding daily capacity
- Suggest breaking large (13-point) tasks into smaller chunks
- Option to auto-distribute LOE suggestions based on task length/description
- Incomplete tasks carry over to next day in "Plan Your Day" view
- Ability to "commit" to a day's plan and track completion

**Integration Points:**
1. **TodoItemCard:** Show LOE badge
2. **TodoDetailScreen:** LOE dropdown in edit form
3. **Navigation Drawer:** LOE filter options
4. **Navigation Graph:** Add PlanYourDayScreen route
5. **AppBar:** Add "Plan" button or Settings menu link to daily planner
6. **ViewModel:** Calculate daily totals, filtering by date + estimated LOE

**Example Workflow:**
1. User creates task: "Implement user authentication"
2. Sets LOE: 8 (Large, ~1 day)
3. Due date: Tomorrow
4. Opens "Plan Your Day" screen
5. Sees: "Tomorrow has 8 LOE points (on target for 21-point day)"
6. User adds 2 more tasks (3 + 5 points)
7. Now showing: "16 / 21 points (76% of capacity, feasible)"
8. User decides 1 task is too much, moves one to next day
9. Plan locked in: "13 / 21 points (62% capacity, good buffer)"

---



### Phase 1: Theming + Quick Wins (Weeks 1-2)
**Goals:** Dark theme, priority repositioning, overdue indicator
- Dark Theme + Theme Switching (4 days)
- Priority Label Repositioning (1 day)
- Overdue Indicator (2 days)
- **Testing & Verification:** 1 day

**Deliverables:**
- Light + Dark color schemes working
- Theme preference saved/restored
- Overdue tasks visually distinct
- Priority badges repositioned

**Commits:**
- `Theme: Add dark mode support and theme switching`
- `UI: Move priority label to end of row`
- `UI: Add overdue indicator to tasks`

---

### Phase 2: Data Organization + Grouping (Weeks 3-5)
**Goals:** Group by due date, persist filters, cache ViewModel state, add LOE estimation
- Group Items by Due Date (3 days)
- Persist Filter State (2 days)
- Save ViewModel State (2 days)
- Add Level of Effort (LOE) Field & Display (2 days)
- **Testing & Verification:** 1 day

**Deliverables:**
- List grouped by semantic dates (Today, Tomorrow, etc.)
- Filters remembered across app restarts
- Instant list display from cache on cold start
- LOE field persisted to markdown frontmatter
- LOE display on task cards (badge near priority)
- LOE selector in TodoDetailScreen edit form

**Commits:**
- `UI: Group tasks by due date with semantic labels`
- `Data: Persist filter state across sessions`
- `Perf: Cache ViewModel state for faster app startup`
- `Feature: Add Level of Effort (LOE) estimation field`

---

### Phase 3: Navigation & Filtering UI (Week 6)
**Goals:** Navigation drawer for filtering (including LOE filter)
- Navigation Drawer (4 days)
- Drawer Integration + Testing (1 day)

**Deliverables:**
- Fully functional filter drawer
- Project/Priority/Status/LOE filtering from drawer
- Real-time filter counts
- Apply/Reset actions

**Commits:**
- `UI: Add navigation drawer for advanced filtering`

---

### Phase 4: Recurring + Projects (Week 7-8)
**Goals:** UI for recurring todos, project management
- Schedule UI for Recurring Todos (2 days)
- Project Management Screen (2 days)
- Project-Based UI Components (1 day)
- **Testing:** 1 day

**Deliverables:**
- Recurrence selector in detail screen
- Next occurrence preview
- Project color customization
- Project filtering in drawer

**Commits:**
- `UI: Add recurrence scheduling interface`
- `UI: Add project management and assignment`

---

### Phase 5: Daily Planning & LOE Analytics (Week 9)
**Goals:** Plan daily capacity using LOE, track insights
- Plan Your Day Screen (3 days)
- LOE Statistics & Dashboard (2 days)
- Integration & Testing (1 day)

**Deliverables:**
- Daily Planner screen showing today's tasks + LOE total
- Capacity bar (0-21 LOE recommended)
- Visual warnings when overcommitted (>21 LOE)
- Filter drawer LOE filter options
- Statistics: average LOE, unestimated task count
- Insights: suggested LOE values, daily load trends

**Commits:**
- `Feature: Add Plan Your Day screen with LOE capacity tracking`
- `Analytics: Add LOE statistics and insights dashboard`

---

## Architecture Changes

### 1. Theme System
- Introduce `ThemeManager` interface (optional, for future extensibility)
- Use Compose Material 3 theming
- Light/Dark color palettes defined in `ui/theme/Colors.kt`

### 2. State Persistence Layer
- Extend `SettingsDataStore` with new preference keys:
  - `themeMode`
  - `lastFilterState`
  - `lastSelectedIds`
  - `cachedTodos`
  - `projectColors`
  - `dailyCapacity` (default 21 LOE points)
- All serialization via Kotlinx Serialization (single library)

### 3. ViewModel Enhancement
- Add `groupedTodos()` function (compute grouping based on FilterState)
- Add cache load/save methods
- Add `getAvailableProjects()` helper
- Add `updateRecurrence()` method
- Add LOE calculation methods:
  - `getTodayLoe(): Int` – sum of all today's task LOE
  - `getLoeForDate(date: LocalDate): Int` – sum for specific date
  - `getLoeCapacityPercent(): Float` – % of recommended 21-point capacity
  - `getLoeStats(): LoeStatistics` – average, unestimated count, distribution
- Add LOE filtering logic to `filteredTodos()`

### 4. UI Layer Reorganization
- Move filter logic from scattered Compose to centralized drawer
- Extract reusable components:
  - `FilterDrawer.kt`
  - `FilterOptionItem.kt`
  - `ProjectTag.kt`
  - `RecurrenceSelector.kt`
  - `TodoListSection.kt`
  - `LoeSelector.kt` (NEW - LOE dropdown)
  - `LoeBadge.kt` (NEW - LOE display badge)

### 5. Repository Additions
- Add `getAvailableProjects()` to TodoRepository
- Scan existing todos to extract unique projects

---

## UI/UX Changes

### Layout Changes
```
Current TodoListScreen:
┌─────────────────────────┐
│ [Filter Chips] [Search] │
├─────────────────────────┤
│ [Task 1]                │
│ [Task 2]                │
│ [Task 3]                │
└─────────────────────────┘

New TodoListScreen with Drawer:
┌─────────────────────────┐
│ ☰ [Search]              │  <- Hamburger menu in AppBar
├─────────────────────────┤
│ [Today]                 │  <- Section header
│ [Task 1]                │
│ [Task 2]                │
│                         │
│ [Tomorrow]              │
│ [Task 3]                │
│                         │
│ [Next Week]             │
│ [Task 4]                │
└─────────────────────────┘

Drawer (when open):
┌──────────────┬──────────┐
│ Filters   [X]│ [List]   │
├──────────────┤          │
│ Projects   ▼ │          │
│ Priority   ▼ │          │
│ Status     ▼ │          │
│ [Reset] [OK] │          │
└──────────────┴──────────┘
```

### Color Theme Examples

**Light Mode:**
- Background: #FFFFFF
- Surface: #F5F5F5
- Primary: #1F51BA (blue)
- Secondary: #546E7A (gray)
- Error: #C62828 (red, for overdue)
- Text: #212121 (dark gray)

**Dark Mode:**
- Background: #121212
- Surface: #1E1E1E
- Primary: #90CAF9 (lighter blue)
- Secondary: #B0BEC5 (light gray)
- Error: #EF5350 (lighter red)
- Text: #FFFFFF

### Accessibility Considerations
- Ensure WCAG AA contrast ratios in both themes
- Announce overdue status to screen readers
- Keyboard navigation for drawer and dropdowns
- Color-blind friendly indicators (not just red for overdue; add icon)

---

## Data Persistence

### SettingsDataStore Extensions
```kotlin
// Theme
val themeMode: Flow<String> = dataStore.data.map { /* "system", "light", "dark" */ }

// Filters
val lastFilterState: Flow<String> = dataStore.data.map { /* JSON */ }
val lastSelectedIds: Flow<String> = dataStore.data.map { /* JSON */ }

// ViewModel Cache
val cachedTodos: Flow<String> = dataStore.data.map { /* JSON */ }
val cacheTimestamp: Flow<Long> = dataStore.data.map { /* epoch millis */ }

// Projects
val projectColors: Flow<String> = dataStore.data.map { /* JSON: { "Work": "#FF5733" } */ }

// LOE & Capacity Planning
val dailyCapacity: Flow<Int> = dataStore.data.map { /* default 21 points */ }
val loeColorThresholds: Flow<String> = dataStore.data.map { /* JSON: { "green": 14, "yellow": 21 } */ }
```

### Markdown Frontmatter Extensions
Recurrence field already persists (Track E2). **New LOE field:**
```yaml
---
id: task-1
title: Buy groceries
loe: 2
recurrence: daily
---
```

---

## Testing Strategy

### Unit Tests
1. **ThemeManager:** Verify light/dark palette selection
2. **GroupedTodos:** Test grouping logic (today, tomorrow, next week)
3. **FilterStateSerializer:** Round-trip serialization/deserialization
4. **TodoCacheLoader:** Verify cache load/save and TTL

### Integration Tests
1. **Theme Switching:** Change theme, restart app, verify persistence
2. **Filter Persistence:** Set filters, navigate away, return, verify restored
3. **ViewModel Cache:** Populate cache, kill process, restart, verify instant load
4. **Drawer Filtering:** Select multiple filters, apply, verify list updates

### UI/E2E Tests (Espresso or Compose Test)
1. Open drawer, select filters, apply
2. Create task with project, verify appears in project filter count
3. Set recurrence, complete task, verify next occurrence created
4. Switch theme, verify colors change

### Manual Testing Checklist
- [ ] Light/Dark theme switching works
- [ ] Theme preference survives app restart
- [ ] Overdue tasks show red/warning indicator
- [ ] List groups by semantic dates
- [ ] Filters persist across screen navigation
- [ ] App shows cached list on cold start
- [ ] Drawer opens/closes smoothly
- [ ] Project filtering works correctly
- [ ] Recurrence selector updates next occurrence preview

---

## Dependencies

### New Libraries to Add
1. **Kotlinx Serialization** (already present for JSON)
   - Used for FilterState, TodoItem cache serialization
2. **Material 3 Theming** (already present via Compose BOM)
   - `androidx.compose.material3:material3`
   - Provides dynamic color scheme APIs

### Existing Libraries (Already in build.gradle.kts)
- Jetpack DataStore (for preferences)
- Jetpack Compose (for UI)
- Kotlin Coroutines (for async operations)

### No New External Dependencies Required
All features can be built with current dependency stack.

---

## Notes for Future Implementation

### Priority
1. **Must Have:** Dark theme, grouping, filter persistence
2. **Should Have:** Drawer, overdue indicator, recurrence UI
3. **Nice to Have:** Project management UI, cache TTL logic

### Open Questions
1. Should projects be hierarchical (nested folders) or flat?
2. What's the default project for tasks not in subfolders?
3. Should recurrence skip completed occurrences or auto-spawn anyway?
4. Should there be a "bulk apply recurrence" action for existing tasks?
5. Should LOE estimates be required (validation) or optional?
6. What's the ideal daily capacity? Is 21 points (8-hour day) correct for all users?
7. Should past-due tasks count toward next day's capacity, or are they separate?
8. Should LOE estimates be tracked as time logs, or just estimates?

### Potential Challenges
1. **Performance:** Large task lists (1000+) with grouping might lag
   - **Solution:** Lazy load groups or paginate within groups
2. **State Complexity:** Multiple independent filters + grouping + caching
   - **Solution:** Use ViewModel as single source of truth; tests verify consistency
3. **Theme Animation:** Smooth color transition when switching themes
   - **Solution:** Use Compose animateColorAsState() for smooth transition

### Future Enhancements (Beyond This Scope)
- Drag-and-drop tasks between projects
- Quick actions context menu (swipe left)
- Subtask progress indicator (checklist percentage)
- Recurring task history (view past occurrences)
- Custom time zones for due dates
- Smart recurrence (e.g., "every weekday")
- Export tasks to .ics calendar format
- **LOE Enhancements:**
  - Burndown chart (LOE completed vs. planned per day)
  - Time tracking integration (log actual hours vs. LOE estimate)
  - Smart LOE suggestions using ML (based on task keywords/description)
  - Weekly capacity planner (spread tasks across week)
  - LOE distribution insights (most tasks are 3-point?)
  - Bulk LOE estimate action (auto-assign to unestimated)
  - Historical LOE accuracy (track estimates vs. actual completion time)

---

**End of Implementation Plan**

Last Updated: July 31, 2026
