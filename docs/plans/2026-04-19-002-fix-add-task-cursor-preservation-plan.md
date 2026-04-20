---
title: fix: Preserve cursor position on Add Task metadata insertions
type: fix
status: active
date: 2026-04-19
origin: none
---

# fix: Preserve cursor position on Add Task metadata insertions

## Overview

Fix the `AddTask` screen so inserting a context, project, or priority does not
jump the caret to the start of the first line. The intended behavior is that the
caret stays on the same task line and at the same logical insertion point it had
before the metadata action, including when the editor contains multiple lines.

## Problem Frame

Today the Add Task editor rebuilds the entire `EditText` contents after metadata
mutations. For contexts and projects, `showListMenu()` and `showTagMenu()` call
`binding.taskText.setText(...)` with no selection restore at all. For priority,
`replacePriority()` does call `restoreSelection(...)`, but it captures the
selection only at mutation time and may still be vulnerable if the toolbar click
changes focus before the selection is read.

The visible symptom Scott reported matches that implementation: after adding a
context/project/priority, the caret lands at character 0 on line 1 instead of
staying where the user was typing.

## Scope Boundaries

- In scope: caret preservation for context (`@`), project (`+`), and priority
  changes on the Add Task screen.
- In scope: multi-line editing behavior on the active line.
- In scope: regression coverage for any extracted pure selection/line-mutation
  logic.
- Out of scope: redesigning the Add Task UI, changing task formatting rules,
  or touching main-list editing behavior.
- Out of scope: the separate quick-add file-selection feature.

## Context & Research

### Relevant Code and Patterns

- `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt` owns the Add Task
  editor, toolbar button handlers, and all text/selection mutations.
- `showTagMenu()` and `showListMenu()` rebuild the full editor contents using
  `binding.taskText.setText(tasks.joinToString("\n") { it.text })` and do not
  restore selection afterward.
- `replacePriority()`, `replaceDueDate()`, and `replaceThresholdDate()` already
  use a `restoreSelection(location, oldLength, moveCursor)` helper after calling
  `setText(...)`.
- `getCurrentCursorLine()` currently derives the active line from
  `binding.taskText.selectionStart`, which means any focus-loss side effect can
  also corrupt line targeting.
- There is currently no `app/src/androidTest/` instrumentation test suite in the
  repo, so regression coverage will either need a small new instrumentation test
  or an extracted pure helper that can be covered by JVM tests.

### Environment Notes

- Repo root: `/home/agent/dev/simpletasks_app`
- Branch at planning time: `master`
- Working tree at planning time appeared clean.
- Local verification blocker in Jarvis environment: `./gradlew test` currently
  fails because `JAVA_HOME` is not set and `java` is missing from `PATH`.

## Likely Root Cause

There are probably two closely related issues:

1. **Selection is discarded when `setText(...)` replaces the full editor
   contents.** This is definitely happening for context/project changes because
   those paths never restore the selection.
2. **Selection may be captured too late, after the toolbar click shifts focus
   away from the `EditText`.** This would explain why priority can still behave
   badly even though it nominally restores the cursor.

Codex should verify both during implementation instead of assuming only the
first one.

## Success Criteria

- Adding a context preserves the caret location relative to the edited task.
- Adding a project preserves the caret location relative to the edited task.
- Adding/changing a priority preserves the caret location relative to the
  edited task.
- Behavior works for a single-line entry and for later lines in a multi-line
  editor.
- The active line is the one mutated when the caret is on line N, not line 1.
- Regression coverage exists for the selection-preservation logic, and manual
  reproduction steps are documented in the implementation summary.

## High-Level Technical Design

Prefer a small, centralized caret snapshot + restore flow inside `AddTask`
rather than one-off fixes in three separate handlers.

Recommended shape:

1. Capture the editor selection before opening or handling metadata actions.
2. Reuse that captured selection when computing the current line and restoring
   the caret after text replacement.
3. Centralize whole-text task-line replacement so context/project/priority all
   follow the same mutation path.
4. Clamp restored offsets to valid bounds after text-length changes.

If Codex finds that focus loss happens *before* the button click handler reads
selection, the capture point should move earlier (for example, button touch-down
or a dedicated saved selection updated from the `EditText`).

## Implementation Units

- [ ] **Unit 1: Reproduce and instrument the selection-loss path**

**Goal:** Confirm whether the bug is only missing restore logic or also stale
selection capture after focus changes.

**Files:**
- Modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`
- Optional create/modify: `app/src/androidTest/...` or JVM helper test file

**Approach:**
- Add temporary reasoning/logging only if needed locally during implementation.
- Check what `selectionStart`/`selectionEnd` and current-line index are at the
  moment each metadata action begins.
- Verify whether priority is mutating the intended line today or only restoring
  to the wrong place.

**Acceptance:**
- Root cause is explicit in the implementation notes, not guessed.

- [ ] **Unit 2: Introduce stable selection snapshot and restore logic**

**Goal:** Make metadata actions preserve caret position consistently.

**Files:**
- Modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`

**Approach:**
- Add a small selection snapshot model or paired `Int` state (`start`, `end`)
  owned by `AddTask`.
- Update that snapshot whenever the editor selection changes, or capture it at a
  point that occurs before toolbar focus stealing.
- Replace direct uses of live `binding.taskText.selectionStart/End` in metadata
  mutation flows with the stable snapshot where appropriate.
- Reuse one restore path after `setText(...)` for context, project, and
  priority.
- Preserve current behavior for dates unless the shared helper improves them
  safely too.

**Acceptance:**
- Context, project, and priority all restore to the expected logical position on
  the edited line.

- [ ] **Unit 3: Consolidate line mutation to avoid divergent behavior**

**Goal:** Reduce the chance of future cursor regressions by moving the three
metadata actions toward one shared mutation pattern.

**Files:**
- Modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`

**Approach:**
- Extract a helper that:
  - resolves the target line from a stable selection snapshot,
  - mutates the corresponding `Task`,
  - rebuilds the editor text,
  - restores selection with the right delta behavior.
- Keep the helper small and local; do not introduce new abstractions outside
  `AddTask.kt` unless necessary.

**Acceptance:**
- Context/project/priority paths are visibly more uniform and easier to reason
  about.

- [ ] **Unit 4: Add regression coverage and verification**

**Goal:** Prevent the bug from quietly returning.

**Files:**
- Create or modify one of:
  - `app/src/test/java/nl/mpcjanssen/simpletask/...`
  - `app/src/androidTest/java/nl/mpcjanssen/simpletask/...`

**Approach:**
- Prefer extracting pure selection/line-resolution logic into a small internal
  helper that can be covered with JVM tests if possible.
- If UI coupling makes that impractical, add a focused instrumentation test for
  the Add Task activity that enters multi-line text, places the caret on a later
  line, performs a metadata action, and asserts both the mutated line and caret
  position.
- Run the narrowest relevant test target first, then broader repo tests if the
  environment supports them.

**Acceptance:**
- At least one automated test fails before the fix and passes after it, or
  Codex documents clearly why Android UI coupling prevented that and what manual
  verification was used instead.

## Risks and Unknowns

- `EditText` selection behavior around focus changes may differ by Android
  version. A fix that only restores after `setText(...)` could still be flaky if
  selection capture happens too late.
- Existing helper `restoreSelection(...)` uses absolute offsets plus delta. That
  may not perfectly preserve intent when metadata is inserted earlier in the
  same line than the caret position; Codex should verify the exact expected
  semantics and adjust if needed.
- The repo currently lacks a ready instrumentation harness, so UI-level
  regression coverage may take more setup than the fix itself.
- Local automated verification may be blocked until a JDK/Android environment is
  available.

## Validation Strategy

### Manual Reproduction

1. Open Add Task.
2. Enter multi-line text with at least 3-4 tasks.
3. Place the caret mid-line on a non-first line.
4. Add a context, then a project, then a priority.
5. Confirm:
   - the correct line is mutated,
   - the caret stays on that line,
   - the caret remains at the pre-action logical position (or the expected
     adjusted position if the inserted token should shift it).

### Automated Validation

- Run focused tests for any new helper/test class.
- Run `./gradlew test` if Java is available.
- If instrumentation is added and an emulator/device is available, run the
  narrowest relevant `connected...AndroidTest` target.

## Handoff Notes for Codex

- Start in `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`; that is the
  bug center.
- Do not treat priority as already solved; verify it against the same root cause.
- Favor a minimal fix that preserves the repo's current style over a broad UI
  rewrite.
- If you need a new helper, keep it package-local or private to `AddTask` unless
  tests force a slightly wider surface.
- If `JAVA_HOME` is still missing in the execution environment, note the test
  limitation explicitly in the implementation summary.
