---
title: feat: favorite-file quick add shortcuts
type: feature
status: active
date: 2026-04-20
origin: chat
---

# feat: favorite-file quick add shortcuts

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Let users create Android quick-add shortcuts that are pinned to a specific favorite todo.txt file, so each shortcut opens normal Add Task UI but saves into that shortcut's target file without permanently switching the app's active list.

**Architecture:** Reuse the existing Android shortcut entry point (`AddTaskShortcut`) and favorite file model (`FavoriteTodoFiles`) to create per-file quick-add shortcuts. Pass the selected favorite file path through shortcut intents into `AddTask`, and make `AddTask`/save flow write to an explicit target file when present instead of always using `TodoApplication.config.todoFile`.

**Tech Stack:** Android Activities/Intents, existing shortcut APIs (`CREATE_SHORTCUT` + `ShortcutManagerCompat` helper), Kotlin, existing `Config`/`TodoList` save flow, favorite file persistence.

---

## Product Decision

Chosen direction:
- **Option 1** from Scott: create file-specific quick-add shortcuts pinned to favorite todo.txt files.
- Shortcut should open the **normal Add Task screen**.
- Saving from that screen should write to the shortcut's target file.
- The app should **not** globally switch active file just because the shortcut was used.

Why this is the better fit:
- Existing code already has a shortcut entry activity (`AddTaskShortcut`) and a shortcut helper (`createShortcut(...)`).
- Favorite files already exist as stable canonical paths in config.
- This avoids adding chooser friction on every use.
- It preserves fast launcher workflows for multiple lists.

---

## Current Code Reality

### Relevant existing pieces
- `app/src/main/java/nl/mpcjanssen/simpletask/AddTaskShortcut.kt`
  - currently creates a single generic Add Task shortcut.
- `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`
  - current Save path always writes through `TodoApplication.config.todoFile`.
- `app/src/main/java/nl/mpcjanssen/simpletask/AddTaskBackground.kt`
  - background quick-add also always writes through `TodoApplication.config.todoFile`.
- `app/src/main/java/nl/mpcjanssen/simpletask/fileswitch/FavoriteTodoFiles.kt`
  - already stores canonical favorite file paths.
- `app/src/main/java/nl/mpcjanssen/simpletask/util/Config.kt`
  - active file is global (`todoFile`); changing it affects whole app.
- `app/src/main/java/nl/mpcjanssen/simpletask/util/Util.kt`
  - already has `createShortcut(...)` built on `ShortcutManagerCompat.requestPinShortcut(...)`.

### Key architectural constraint
Right now Add Task assumes “save to current active file”.
That is the real implementation gap.
The fix must allow:
- `AddTask` launched from shortcut
- optional explicit target file path in intent
- saving to that file without changing app-global active file

---

## Acceptance Criteria

### User-facing behavior
- User can create a launcher shortcut for any file already present in favorites.
- Each shortcut is labeled clearly enough to distinguish files with the same base name.
- Tapping the shortcut opens the normal Add Task screen.
- When user saves, the new task is written to the shortcut's configured file.
- Using the shortcut does **not** permanently switch the app's active file.
- If the target file is no longer available, the user gets a clear error and no silent fallback to the wrong file.

### Technical behavior
- `AddTask` supports an optional explicit save target via intent extra.
- Save path uses explicit target file when present; otherwise existing behavior remains unchanged.
- Existing generic add-task flows still work.
- Favorite-file quick-add shortcut creation is covered by pure/unit tests where practical.
- Save-target resolution is covered by unit tests.

---

## Implementation Strategy

### Task 1: Define explicit quick-add target intent contract

**Objective:** Add a stable intent extra for a quick-add target file path, separate from the app-global active file.

**Files:**
- Modify: `app/src/main/java/nl/mpcjanssen/simpletask/Constants.kt`
- Test: `app/src/test/...` if pure helper extracted

**Step 1: Write failing test or helper contract assertion**
Create a pure helper or small testable contract around explicit target resolution.

**Step 2: Add intent extra constant**
Add a constant such as:
- `EXTRA_TARGET_TODO_FILE = "target_todo_file"`

**Step 3: Verify no legacy constant collisions**
Confirm this is new and isolated from existing shortcut/filter/widget extras.

**Step 4: Commit**
```bash
git add app/src/main/java/nl/mpcjanssen/simpletask/Constants.kt
git commit -m "feat(shortcuts): add explicit quick-add file target extra"
```

---

### Task 2: Extract save-target resolution helper

**Objective:** Make Add Task save destination explicit and testable.

**Files:**
- Create: `app/src/main/java/nl/mpcjanssen/simpletask/QuickAddTarget.kt` (or similar)
- Modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`
- Test: `app/src/test/java/nl/mpcjanssen/simpletask/QuickAddTargetTest.kt`

**Step 1: Write failing unit tests**
Test cases should cover:
- explicit shortcut target path → uses that file
- missing/blank target → falls back to `config.todoFile`
- invalid/nonexistent file path behavior decision (likely fail with message, not silent fallback)

**Step 2: Implement minimal helper**
Helper shape example:
```kotlin
internal object QuickAddTarget {
    fun resolve(intentTargetPath: String?, fallback: File): File
}
```

**Step 3: Thread helper into `AddTask.saveTasksAndClose()`**
Change save call from:
```kotlin
todoList.notifyTasklistChanged(TodoApplication.config.todoFile, save = true, refreshMainUI = false)
```
to resolved target file.

**Step 4: Keep existing behavior unchanged when no target extra exists**
Normal FAB/add-task flows should still write to active file.

**Step 5: Verify tests pass**
```bash
./gradlew :app:testCloudlessDebugUnitTest --tests nl.mpcjanssen.simpletask.QuickAddTargetTest
```

**Step 6: Commit**
```bash
git add app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt app/src/main/java/nl/mpcjanssen/simpletask/QuickAddTarget.kt app/src/test/java/nl/mpcjanssen/simpletask/QuickAddTargetTest.kt
git commit -m "feat(add-task): support explicit quick-add target files"
```

---

### Task 3: Update shortcut creation flow to choose a favorite file

**Objective:** Replace the current generic shortcut creator with a favorite-file picker that creates per-file shortcuts.

**Files:**
- Modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTaskShortcut.kt`
- Possibly create: `app/src/main/res/layout/...` if a custom picker UI is needed
- Modify: `app/src/main/res/values/strings.xml`
- Test: pure helper in test if shortcut label/id generation is extracted

**Step 1: Explore simplest UI**
Prefer a lightweight `AlertDialog` list over a new screen unless customization is required.

**Step 2: If no favorites exist, show a clear message and abort**
Do not create a generic shortcut when the user explicitly entered favorite-file shortcut flow.

**Step 3: Build shortcut intent per favorite**
Shortcut target intent should launch `AddTask` and include:
- `Constants.EXTRA_TARGET_TODO_FILE`
- maybe user-facing label/name extras if needed for future diagnostics

**Step 4: Generate stable shortcut labels/ids**
Need labels that distinguish files with same name.
Likely format:
- `Add to inbox.txt`
- if duplicates, include parent folder segment

**Step 5: Reuse or adapt `createShortcut(...)` helper if launcher-pinned path is used**
If legacy `CREATE_SHORTCUT` result flow is required for compatibility, keep that behavior but populate the selected favorite's target intent.
If current Android path allows direct pinning from activity, evaluate whether to switch; prefer compatibility if unsure.

**Step 6: Add helper tests for label/id generation**
Test same-name files in different directories remain distinguishable.

**Step 7: Commit**
```bash
git add app/src/main/java/nl/mpcjanssen/simpletask/AddTaskShortcut.kt app/src/main/res/values/strings.xml
# plus any helper/test/layout files
git commit -m "feat(shortcuts): create quick-add shortcuts for favorite files"
```

---

### Task 4: Handle invalid or unavailable target files safely

**Objective:** Prevent tasks from being silently written to the wrong file.

**Files:**
- Modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`
- Maybe modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTaskBackground.kt` if background flows will later share logic
- Test: `QuickAddTargetTest.kt`

**Step 1: Define behavior**
Recommended:
- if explicit target path is present but invalid/unavailable, show a toast/dialog and do not save
- do **not** silently fall back to active file

**Step 2: Implement minimal validation**
Likely check file/path availability in the same way current file handling assumes local/canonical file objects.
Be cautious not to over-engineer network/file-store probing in the first pass.

**Step 3: Test failure mode**
Write a unit test for explicit bad target path behavior.

**Step 4: Commit**
```bash
git add app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt app/src/test/java/nl/mpcjanssen/simpletask/QuickAddTargetTest.kt
git commit -m "fix(shortcuts): block invalid quick-add target files"
```

---

### Task 5: Optional polish — show target file in Add Task UI when launched from shortcut

**Objective:** Reduce ambiguity so users know which list the shortcut will save into.

**Files:**
- Modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`
- Maybe modify layout/strings

**Approach:**
Small non-invasive signal only, such as subtitle/help text/toast on open.
Skip if it complicates UI too much for first release.

**Acceptance:**
- User can tell where shortcut-launched Add Task will save, or the behavior is otherwise obvious from shortcut label alone.

---

### Task 6: Update docs and changelog

**Objective:** Document how favorite-file quick-add shortcuts work.

**Files:**
- Modify: `README.md` if shortcut usage is documented there
- Modify: `CHANGELOG.md`
- Optionally add a short note in user help docs if shortcut behavior is documented in assets

**Step 1: Add Keep a Changelog entry**
Under `Unreleased` → `Added`:
- Quick-add launcher shortcuts can now target specific favorite todo.txt files

**Step 2: Add user-facing docs**
Document:
- file must already be a favorite
- shortcut opens normal Add Task screen
- saving goes to the pinned file without switching active list

**Step 3: Commit**
```bash
git add CHANGELOG.md README.md
git commit -m "docs(shortcuts): document favorite-file quick add"
```

---

## Suggested Design Notes

### Shortcut creation UX
Best first-pass UX:
- user long-presses app / invokes Add Task shortcut creation entry
- `AddTaskShortcut` presents favorite files in a simple picker
- selecting a file creates a shortcut pinned to that exact favorite

### Labeling strategy
Need to handle duplicate filenames.
Recommended rule:
- if filename unique among favorites: `Add to <filename>`
- if not unique: `Add to <filename> — <parent folder name>`

### Save semantics
Strong recommendation:
- explicit shortcut target file should override only the save destination for that Add Task session
- do not mutate `Config.todoFile`
- do not call `switchTodoFile(...)`

This avoids surprising file switches in the rest of the app.

---

## Risks and Unknowns

- The current `AddTask` editing flow updates `TodoApplication.todoList`, which is loaded from the active file. If a shortcut targets a *different* file than the currently loaded one, appending via `todoList.update(...)` may accidentally mix state unless save logic is carefully isolated.
- The real implementation may require bypassing in-memory `pendingEdits` assumptions when target file differs from active file.
- For first version, the safest supported scope may be **new task creation only**, not editing existing tasks via shortcut-targeted Add Task.
- Android shortcut APIs differ across launchers and Android versions; compatibility behavior should be verified on the device Scott actually uses.

---

## Likely safest scope for V1

To keep this reliable, V1 should likely support:
- **creating new tasks** via file-specific Add Task shortcuts
- not editing existing tasks into non-active files
- not background quick-add selection yet

That is enough to satisfy Scott's stated use case.

---

## Recommended next implementation order

1. Add explicit target-file intent contract
2. Extract save-target helper + tests
3. Make Add Task save to explicit target file without global file switch
4. Build favorite-file shortcut picker in `AddTaskShortcut`
5. Add docs/changelog

---

## Verification Checklist

Before shipping:
- [ ] A favorite-file shortcut can be created for at least 2 different files
- [ ] Shortcut labels are distinguishable
- [ ] Using shortcut A saves only to file A
- [ ] Using shortcut B saves only to file B
- [ ] App's active file remains unchanged after saving through shortcut
- [ ] Invalid target path shows error and does not save to wrong file
- [ ] Existing generic add-task flows still work
- [ ] `CHANGELOG.md` updated in same commit as user-facing feature

---

## Ready-for-implementation summary

**Recommended build:** file-specific pinned quick-add shortcuts backed by favorites, opening normal Add Task UI and saving to an explicit per-shortcut file target without globally switching the app's active todo.txt file.

This is the best fit for Scott's workflow and the current codebase, but the implementation must treat “save to explicit file” as the central design problem rather than “create shortcut” alone.
