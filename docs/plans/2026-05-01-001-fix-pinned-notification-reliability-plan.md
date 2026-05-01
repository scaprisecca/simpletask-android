---
title: fix: pinned notification reliability follow-up plan
type: implementation-plan
status: active
date: 2026-05-01
origin: chat
source_doc: none
related_docs:
  - docs/brainstorms/2026-04-23-pinned-notification-improvement-requirements.md
  - docs/plans/2026-04-23-001-pinned-notification-persistence-plan.md
  - docs/plans/2026-04-26-002-scheduled-pinned-notification-time-implementation-plan.md
  - docs/manual_tests/2026-04-26-scheduled-pinned-notification-checklist.md
---

# pinned notification reliability follow-up plan

> **For Hermes / Codex:** Keep scope tight to the three regressions Scott just reported: stale in-app pinned-state refresh, missing restore-after-reboot until app open, and broken notification tap behavior for tasks in a non-active todo file. Do not widen this into new reminder UX or identity redesign work.

created: 2026-05-01
status: active
source_doc: none

## objective
- fix the remaining pinned-notification reliability issues in the current implementation
- make pinned-state UI refresh immediately after pin and unpin actions, including notification-originated unpin
- restore posted pinned notifications after reboot without requiring a manual app open
- make tapping a pinned notification from another todo file switch to the correct file and open the task editor reliably
- preserve the recent duplicate-text-in-same-file improvement as a regression area

## scope
### included
- pinned-state refresh behavior in the task list UI and action/menu state
- boot/package-replaced restore behavior for already-posted pinned notifications
- notification content-tap routing for pinned tasks in another todo file
- focused JVM test additions for notification helpers and routing logic
- manual-test checklist updates for the regressions Scott found

### excluded
- new reminder UX or scheduler changes beyond what is needed to fix restore behavior
- changing the pinned-record identity model away from file path + text + occurrence index
- multi-task notifications
- due-date automation or broader reminder-center work
- unrelated notification styling changes

## current_findings
- `Simpletask.togglePinnedNotification(...)` and `showPinnedNotificationManager(...)` call `invalidateOptionsMenu()`, but pinned-state persistence and `pinnedTaskDisplayStates` updates happen asynchronously in `PinnedTaskNotificationManager`, so the menu/list can re-render before the manager state finishes updating.
- `TaskAdapter` decorates task text from `PinnedTaskNotificationManager.decorateTaskText(...)`, but pin/unpin flows do not currently broadcast a dedicated UI refresh after `refreshPinnedTaskKeys()` completes.
- `UnpinTaskNotification` delegates to `unpinTaskByKey(...)`, which updates DB state on a background executor but does not notify the main activity to refresh selection/menu/list state.
- `PinnedNotificationBootReceiver` currently calls `restorePinnedNotifications(...)` and then `loadTodoList(...)`, but Scott still sees posted pins only after opening the app, so boot restore needs to be treated as an independent delivery path rather than something that passively waits for the normal activity startup/load cycle.
- `PinnedTaskNotificationManager.postNotification(...)` currently routes notification body taps directly to `AddTask` with `EXTRA_PINNED_TASK_KEY` and `EXTRA_TARGET_TODO_FILE`.
- the main activity already contains an unfinished cross-file open flow in `Simpletask.handlePinnedTaskOpenIntent(...)` plus `continuePendingPinnedTaskOpenIfReady()`, but the notification content intent does not currently use that path.
- product direction for this repo is already settled: when the pinned task belongs to another todo file, the app should switch to that file first before continuing the open/edit flow.

## relevant_existing_patterns
- `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
  - owns menu/action state, selection clearing, broadcast handling, and the existing pending cross-file pinned-task-open state
- `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
  - central owner of pin/unpin/reconcile/post/restore behavior and current notification content/action intents
- `app/src/main/java/nl/mpcjanssen/simpletask/PinnedNotificationBootReceiver.kt`
  - boot/package-replaced restore entry point
- `app/src/main/java/nl/mpcjanssen/simpletask/TodoApplication.kt`
  - app startup and file-switch behavior; `switchTodoFile(...)` currently commits config before the new file is proven loaded
- `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`
  - editor entry point and explicit-target pinned-task edit support
- `app/src/main/java/nl/mpcjanssen/simpletask/UnpinTaskNotification.kt`
  - notification-originated unpin action service
- `app/src/main/java/nl/mpcjanssen/simpletask/MarkTaskDone.kt`
  - notification-originated complete action service
- `app/src/main/java/nl/mpcjanssen/simpletask/util/Util.kt`
  - existing local-broadcast helpers for list, selection, widget, and state-indicator refreshes
- `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskTaskResolverTest.kt`
  - existing resolver tests to extend
- `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskDeliveryStateTest.kt`
  - existing delivery-state tests to extend if restore semantics change
- `docs/manual_tests/2026-04-26-scheduled-pinned-notification-checklist.md`
  - current manual checklist that should absorb these new regression expectations

## implementation_units
### 1. add an explicit pinned-state UI refresh signal
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Constants.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/util/Util.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
- changes:
  - add one dedicated local broadcast for pinned-notification state changes rather than piggybacking on task-list-changed semantics
  - emit that broadcast only after `PinnedTaskNotificationManager` finishes mutating DB/display-state state for:
    - pin
    - unpin
    - restore/reconcile deletions or updates
    - scheduled-trigger promotion to posted
    - done cleanup
  - in `Simpletask`, handle the new broadcast by:
    - refreshing the visible task adapter
    - invalidating the options menu
    - refreshing selection visuals if needed
    - refreshing calendar-day list if the app is in calendar mode and pinned decorations are visible there
- dependencies:
  - none
- risks:
  - broadcasting too early will preserve the race and keep the bug intermittent
  - reusing `TASK_LIST_CHANGED` would overstate what happened and could trigger unnecessary reload behavior
- tests:
  - extend: `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskDeliveryStateTest.kt` only if a new helper is introduced for visible-state transitions
- acceptance:
  - pinning a task updates the in-app pin indicator without an extra tap
  - unpinning from the notification shade removes the in-app indicator without needing another interaction
  - the toolbar label reflects current pin state on first render after the operation completes

### 2. make boot/package restore a first-class notification-delivery path
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/PinnedNotificationBootReceiver.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskTaskResolver.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/TodoApplication.kt`
- changes:
  - keep restore logic resolver-first and file-scoped; do not rely on whichever todo file is active in memory during boot
  - separate “restore posted notifications from persisted records” from “reload the main todo list for normal app state” so the notification tray can be repopulated even if the main activity never opens
  - make boot receiver completion wait for restore work, not for a later activity launch side effect
  - review whether `loadTodoList(...)` from the boot receiver is actually needed for restore success; if it is retained, keep it explicitly secondary to notification restoration
  - add narrow logging around boot/package restore outcome so future device-level failures can be diagnosed quickly
- dependencies:
  - unit 1 recommended first so restore-triggered state changes can refresh UI later when the app opens
- risks:
  - file availability can still vary by backend or device boot timing; restore logic should no-op safely instead of deleting good records too aggressively on transient boot races
  - if restore and normal reconcile both run simultaneously, duplicate posts are possible unless record state transitions stay idempotent
- tests:
  - extend: `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskTaskResolverTest.kt`
  - add helper-level tests only if restore policy extraction creates a pure helper
- acceptance:
  - an already-posted pin reappears after reboot without opening the app manually
  - package-replaced/app-restart restore still works
  - restore remains safe when the active file in config is not the record file

### 3. route notification taps through the guarded main-activity file-switch flow
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/TodoApplication.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`
- changes:
  - change the notification content intent so body taps launch `Simpletask` with `EXTRA_OPEN_PINNED_TASK`, `EXTRA_PINNED_TASK_KEY`, and `EXTRA_TARGET_TODO_FILE`
  - make `Simpletask.handlePinnedTaskOpenIntent(...)` the single authoritative entry path for pinned-notification open behavior
  - harden the pending-open flow so it only opens the editor after the target file is actually active and loaded
  - review `TodoApplication.switchTodoFile(...)` behavior because it currently commits the new todo file into config before proving the new list load succeeded; either guard the switch or add rollback/failure handling so pinned opens cannot leave the app in a mismatched file/content state
  - keep `AddTask` explicit-target edit support compatible with the final opened path, but stop relying on direct notification-to-`AddTask` launch for cross-file open correctness
- dependencies:
  - unit 2 because the cross-file open path depends on file-scoped task resolution staying reliable
- risks:
  - if the file-switch completion signal is wrong, the app can still black-screen or open the wrong file/task
  - switching files while unsaved changes are pending needs an explicit safe outcome; do not silently bypass the existing protection
- tests:
  - extend: `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskTaskResolverTest.kt`
  - add pure helper tests if file-switch gating is extracted into a JVM-safe helper
- acceptance:
  - tapping a pinned notification while another todo file is active switches back to the source file
  - after the file switch completes, the correct task editor opens
  - `Done` and `Unpin` still continue to affect the correct underlying file/task

### 4. refresh manual verification and protect duplicate-text behavior
- files:
  - modify: `docs/manual_tests/2026-04-26-scheduled-pinned-notification-checklist.md`
  - modify: `CHANGELOG.md`
- changes:
  - update the manual checklist language so the observed regressions become explicit pass/fail items:
    - immediate in-app refresh after pin/unpin
    - reboot restore without app open
    - cross-file notification tap opens correct file/editor
  - keep duplicate identical-text pinning in the checklist as a regression area that must still pass after the fixes
  - record the user-facing bugfix in `CHANGELOG.md` because this is a user-visible behavior change in this repo
- dependencies:
  - units 1–3 complete
- risks:
  - easy to skip because it is non-code work; do not leave the test artifact stale
- tests:
  - manual only
- acceptance:
  - checklist matches the behavior we expect Scott to verify on-device
  - changelog entry ships in the same implementation commit

## sequencing
1. implement the dedicated pinned-state refresh signal so the UI state bug becomes deterministic and locally verifiable
2. harden restore behavior so boot/package restore no longer depends on app-open side effects
3. reroute notification body taps through `Simpletask` and guard cross-file switching before editor open
4. update manual checklist and changelog
5. run focused JVM tests for notification helpers, then rebuild with the cloudless helper script

## risks_and_unknowns
- **boot restore may still vary by device storage/backend timing**
  - mitigation: keep restore idempotent, resolver-first, and log enough context to diagnose future failures without widening scope
- **file-switch behavior is already risky outside pinned notifications**
  - mitigation: make the pinned-open path use explicit gating and failure cleanup rather than assuming `config.todoFile` implies the list is ready
- **duplicate identical task text in the same file remains architecturally fragile**
  - mitigation: do not touch keying strategy in this fix; explicitly preserve existing occurrence-index behavior with regression coverage
- **UI refresh fixes can look correct while still racing occasionally**
  - mitigation: emit refresh broadcasts only after executor-side state changes complete, not immediately from button handlers

## validation_strategy
- focused JVM tests:
  - `./gradlew :app:testCloudlessDebugUnitTest --tests nl.mpcjanssen.simpletask.notifications.PinnedTaskDeliveryStateTest --tests nl.mpcjanssen.simpletask.notifications.PinnedTaskTaskResolverTest --tests nl.mpcjanssen.simpletask.notifications.PinnedTaskAlarmSchedulerTest --tests nl.mpcjanssen.simpletask.notifications.PinnedTaskKeyTest --tests nl.mpcjanssen.simpletask.notifications.PinnedTaskFileEditTest`
- full local build:
  - `./scripts/build_cloudless.sh`
- manual verification on device/emulator using `docs/manual_tests/2026-04-26-scheduled-pinned-notification-checklist.md`, with emphasis on:
  - pin/unpin refresh in the active UI
  - unpin from notification shade
  - reboot restore of an already-posted pin
  - notification tap for a task in another todo file
  - duplicate-text regression

## handoff_notes
- do not redesign pinned identity in this pass
- prefer small, explicit fixes over new abstractions unless a helper materially improves correctness
- keep the current scheduled-pin feature intact; this is a reliability follow-up, not a feature rewrite
- if cross-file notification-open correctness conflicts with the current direct `AddTask` content intent, treat `Simpletask` as the authoritative coordinator and make the intent path consistent everywhere
- include `CHANGELOG.md` in the same implementation commit if user-facing behavior changes are made
