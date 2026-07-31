# SilverMeme — Obsidian Todo Manager

An Android application that manages TODO items stored as **Obsidian-compatible Markdown files** inside a **git repository**. Edit tasks on your laptop in Obsidian, push them to a private GitHub repository, and view/edit them on Android — all in sync.

---

## Features

| Feature | Details |
|---------|---------|
| **Markdown-backed tasks** | Every TODO item is a `.md` file with YAML frontmatter — fully readable in Obsidian |
| **Git sync** | Pull changes from any HTTPS git remote (GitHub, GitLab, Gitea …) |
| **Auto push** | Saves are immediately committed and pushed when a remote is configured |
| **Metadata** | Due date, priority (low/medium/high/urgent), location, tags |
| **Filtering & sorting** | Filter by priority, overdue, completion; sort by due date, priority, or title |
| **Search** | Full-text search across title, notes, and tags |
| **Clean UI** | Material You (Material 3) with dynamic colour, dark mode support |

---

## Markdown / Obsidian Format

Each task is stored as a file under `Tasks/` inside the vault root:

```
vault/
└── Tasks/
    ├── Buy groceries.md
    ├── Call dentist.md
    └── ...
```

Example file (`Tasks/Buy groceries.md`):

```markdown
---
id: a1b2c3d4-e5f6-7890-abcd-ef1234567890
title: Buy groceries
status: open
priority: high
due: 2024-03-15
location: Superstore
tags:
  - shopping
  - errands
created: 2024-03-01T09:00:00
updated: 2024-03-01T09:00:00
---

## Notes

Don't forget the reusable bags.
```

The format is 100% compatible with Obsidian's YAML frontmatter and can be edited directly from the Obsidian desktop/mobile app.

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- A private GitHub (or any HTTPS git) repository for your vault

### Building

1. **Clone this repo** and open it in Android Studio.
2. Generate the Gradle wrapper jar (if not already present):
   ```bash
   gradle wrapper --gradle-version 8.4
   ```
3. Sync Gradle, then **Run** the app on an emulator or physical device.

### Setting up the vault

1. Open the app and tap **⋮ → Settings**.
2. Enter your git remote URL (e.g. `https://github.com/you/vault.git`).
3. Enter your GitHub username and a **Personal Access Token** (PAT) with `repo` scope.
4. Tap the ✓ FAB to save.
5. Return to the task list and tap **↻ (Sync)** — the app will clone the repository on first run.

### Using Obsidian on desktop

1. Open the vault folder (same repository) in Obsidian.
2. Create tasks in the `Tasks/` folder with the frontmatter above.
3. Commit and push from your terminal or the Obsidian Git plugin.
4. On Android, tap **↻ Sync** to pull the latest changes.

---

## Architecture

```
app/
└── src/main/java/com/tmstoner/silvermeme/
    ├── data/
    │   ├── model/          # TodoItem, Priority
    │   ├── repository/     # TodoRepository, GitRepository (JGit)
    │   └── storage/        # MarkdownFileManager, SettingsDataStore
    ├── ui/
    │   ├── components/     # TodoItemCard, PriorityChip
    │   ├── navigation/     # AppNavGraph
    │   ├── screens/        # TodoListScreen, TodoDetailScreen, SettingsScreen
    │   └── theme/          # Material 3 theme
    └── viewmodel/          # TodoViewModel, SettingsViewModel
```

Key libraries:
- **Jetpack Compose + Material 3** — UI
- **JGit 5.13** — git operations on Android
- **Jetpack DataStore** — persisting settings (git URL, PAT, author info)
- **Navigation Compose** — screen routing
- **Kotlin Coroutines** — async I/O

---

## Security Notes

- The Personal Access Token is stored in DataStore on the device's internal storage (app-private).
- Only HTTPS transports are supported (no plain HTTP; `usesCleartextTraffic="false"`).
- The PAT is never logged or included in git commits.

---

## Running the Tests

```bash
./gradlew :app:test
```

Unit tests cover the `MarkdownFileManager` (frontmatter parsing, serialisation round-trips, CRUD lifecycle) and `Priority` enum parsing.

---

## License

[Apache 2.0](LICENSE)
