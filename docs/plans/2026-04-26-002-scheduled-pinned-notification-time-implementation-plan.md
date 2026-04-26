---
title: feat: scheduled pinned notification time implementation plan
type: implementation-plan
status: active
date: 2026-04-26
origin: chat
source_doc: docs/brainstorms/2026-04-23-pinned-notification-improvement-requirements.md
related_docs:
  - docs/plans/2026-04-26-001-scheduled-pinned-notification-time-plan.md
---

# scheduled pinned notification time implementation plan

> **For Hermes:** Use this plan as the execution artifact. Keep scope to exact-time manual scheduling for pinned notifications. Do not widen into due-date automation or a generalized reminder center.

created: 2026-04-26
status: active
source_doc: docs/brainstorms/2026-04-23-pinned-notification-improvement-requirements.md

## objective
- add the ability to pin a task notification for a specific future time
- make scheduling the default pin flow, with the picker defaulting to `now`
- preserve immediate pinning by allowing the user to confirm the default time without editing
- keep reminder state app-local and out of todo.txt task text
- reuse the existing pinned-notification architecture instead of creating a second reminder system

## scope
### included
- one-task-at-a-time scheduled pinning from the existing task pin action
- local DB state for scheduled-vs-posted pin records
- exact/best-effort Android alarm scheduling
- alarm restoration after reboot and package replace
- notification posting, open/edit, done, and unpin behavior for scheduled-origin notifications
- switching to the reminder’s todo file before open/edit when needed
- regression coverage for notification-state helpers and resolver behavior

### excluded
- auto-pinning from due dates or thresholds
- multi-task reminder grouping
- sync of reminder state across devices
- storage of timestamps in todo.txt task lines
- full reminder-management screen
- true v1 rescheduling UI; `unpin and create again` is acceptable

## locked_product_decisions
- tapping the current pin action should open the scheduling flow by default
- the date/time picker should default to `now`
- confirming a time that is already in the past should post immediately
- already-scheduled or already-posted pins should use the in-app label `Manage notification`
- for tasks in another todo file, the app should switch to that file first before open/edit flows continue
- v1 does not need a dedicated reschedule flow

## relevant_existing_patterns
- `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
  - owns the current toolbar/context action routing via `togglePinnedNotification(...)`
  - already contains date picker usage patterns and several small dialog flows
- `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
  - central notification owner for pin/unpin/post/reconcile flows
  - currently assumes the active todo file for task lookup and immediate posting
- `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskRecord.kt`
  - already includes `triggerAtMillis` and `triggerMode`, but they are currently unused behaviorally
- `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskKey.kt`
  - central task-key generation and pinned-record retargeting helper
- `app/src/main/java/nl/mpcjanssen/simpletask/PinnedNotificationBootReceiver.kt`
  - current restore entry point for pinned notifications after reboot/package replace
- `app/src/main/java/nl/mpcjanssen/simpletask/MarkTaskDone.kt`
  - current done-action service path for pinned notifications
- `app/src/main/java/nl/mpcjanssen/simpletask/UnpinTaskNotification.kt`
  - current unpin-action service path
- `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`
  - current edit entry point from pinned notifications
  - important because edit/open from scheduled notifications may target a different file
- `app/src/main/java/nl/mpcjanssen/simpletask/TodoApplication.kt`
  - app startup, Room init, notification channel creation, broadcast handling, and existing `AlarmManager` usage
- `app/src/main/java/nl/mpcjanssen/simpletask/dao/Daos.kt`
  - Room schema version and DAO definitions
- `app/src/main/AndroidManifest.xml`
  - receiver/service declarations for boot restore and notification actions
- `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskKeyTest.kt`
  - current JVM-safe notification helper test location to extend

## architecture_decisions
### use one pinned-record model for both scheduled and posted notifications
Do not create a second reminder table. `PinnedTaskRecord` should remain the single source of truth, but it needs a clear state model so the app can tell whether a record is:
- waiting for a future trigger
- already posted as an active notification
- no longer valid and ready to remove

### fix file-scoped task resolution before adding the alarm receiver
The main blocker is not the time picker. It is that the current notification manager resolves tasks through the active file only. Scheduled notifications will be unreliable until task lookup can resolve against `record.todoFilePath` even when another file is active.

### keep v1 UI small
Do not build a separate reminder screen. Reuse the current pin action, open a date/time picker flow, and use `Manage notification` for an already existing record.

### prefer best-effort exactness over blocking the feature
Target SDK is 31. Exact-alarm restrictions matter. The implementation should try exact scheduling where allowed, but it should still fall back gracefully instead of disabling scheduled pinning outright.

## implementation_units

### 1. formalize pinned notification delivery state
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskRecord.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/dao/Daos.kt`
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskDeliveryState.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
- changes:
  - replace implicit behavior with explicit state helpers for:
    - immediate/post-now pins
    - scheduled-not-yet-posted pins
    - posted notifications that originated from a schedule
  - decide whether to encode state as:
    - a new persisted field, or
    - a small helper that derives safe state from `triggerAtMillis`, `triggerMode`, and a new delivery marker
  - keep the API small and local to notification code
- dependencies:
  - none; do this first so later units can code against explicit states
- risks:
  - inferring state from `triggerAtMillis` alone will be brittle and lead to duplicate post/cancel edge cases
- tests:
  - create: `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskDeliveryStateTest.kt`
- acceptance:
  - a record can clearly represent scheduled-but-not-posted vs already-posted
  - manager code no longer relies on null/nonnull trigger fields as ad hoc state

### 2. extract explicit-file task resolution
- files:
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskTaskResolver.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/MarkTaskDone.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`
- changes:
  - centralize task lookup by `PinnedTaskRecord` so the resolver can:
    - use `TodoApplication.todoList.allTasks()` when the record file matches the active file
    - otherwise load lines from the record file via `FileStore.loadTasksFromFile(...)`
    - return both resolved `Task` and file-context metadata needed by open/edit/done flows
  - move all active-file assumptions out of manager methods that currently depend on `currentTodoFilePath()`
  - make `findTaskForPinnedKey(...)`, `completeTaskFromNotification(...)`, and notification tap/edit resolution go through the same helper path
- dependencies:
  - unit 1 state cleanup
- risks:
  - duplicate task text in the same file remains a known ambiguity with the current key strategy
  - resolver code must stay JVM-test-safe where possible
- tests:
  - create: `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskTaskResolverTest.kt`
  - extend: `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskKeyTest.kt`
- acceptance:
  - scheduled notification flows can resolve tasks even when a different file is active
  - all notification actions share one resolution strategy

### 3. add alarm scheduling and cancellation helpers
- files:
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskAlarmScheduler.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Constants.kt`
- changes:
  - encapsulate `AlarmManager` interactions in a small helper instead of scattering them across receiver and manager code
  - support:
    - schedule once for a record
    - cancel a scheduled record
    - reschedule by cancel-then-schedule
    - best-effort exactness with fallback if exact alarms are not available
  - derive stable request codes from the pinned record key
- dependencies:
  - unit 1 state model
  - unit 2 resolver assumptions defined
- risks:
  - duplicate or stale alarms if request-code derivation is inconsistent with record lifecycle
- tests:
  - create: `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskAlarmSchedulerTest.kt`
- acceptance:
  - scheduling logic is reusable and isolated from UI code
  - manager can ask for schedule/cancel without directly handling alarm details

### 4. add the alarm receiver and boot/package restore wiring
- files:
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/PinnedTaskAlarmReceiver.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/PinnedNotificationBootReceiver.kt`
  - modify: `app/src/main/AndroidManifest.xml`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/TodoApplication.kt`
- changes:
  - add a broadcast receiver dedicated to future pin triggers
  - receiver should:
    - load the pinned record from DB
    - verify it still exists and is still in scheduled state
    - resolve the task through the resolver
    - post the notification or no-op/cleanup if the task is gone
  - on boot/package replace:
    - reload current records
    - re-register alarms for scheduled records
    - repost active notifications for posted records
- dependencies:
  - units 1–3
- risks:
  - boot timing can expose file-availability issues with synced backends
  - restore logic can accidentally double-post if scheduled-vs-posted state is unclear
- tests:
  - helper-level tests in notification package
  - manual reboot/package-replace verification
- acceptance:
  - scheduled pins survive reboot and fire once
  - posted pins remain restorable after app restart/package replace

### 5. update the manager for scheduled and posted flows
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
- changes:
  - split current `pinTask(...)` behavior into explicit flows such as:
    - schedule pin
    - post immediate pin
    - post scheduled pin when alarm fires
    - unpin record and cancel either scheduled alarm or active notification
  - make reconcile logic aware of scheduled records so it can:
    - cancel invalid scheduled records
    - update records when text changes before trigger
    - avoid posting a future record prematurely
  - ensure `isPinned(...)` and UI-state queries can detect both scheduled and posted records
- dependencies:
  - units 1–4
- risks:
  - manager is already doing a lot; avoid mixing scheduling, task resolution, and UI-specific choices in one method
- tests:
  - extend new notification helper tests
- acceptance:
  - manager cleanly handles immediate and scheduled pin lifecycles without duplicated logic

### 6. add the scheduling UI to the existing task action
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
  - modify: `app/src/main/res/values/strings.xml`
  - possibly create: one small layout under `app/src/main/res/layout/` if a combined date/time dialog is simpler than chained dialogs
- changes:
  - change the current pin action so:
    - if the task has no pin record, open scheduling flow
    - default date/time to `now`
    - if confirmed time is already in the past, call immediate post path
    - if confirmed time is in the future, create/update a scheduled record
  - if the task already has a record, change toolbar label to `Manage notification`
  - keep v1 manage flow small:
    - at minimum, expose `Unpin`
    - optionally show read-only scheduled time if cheap to add
- dependencies:
  - unit 5 manager APIs
- risks:
  - too much manage-UI ambition will bloat v1 unnecessarily
- tests:
  - manual UI regression checks
- acceptance:
  - a user can create an immediate or future pin through one default scheduling flow
  - existing pin action label behavior remains correct

### 7. support switched-file open/edit behavior from notifications
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/TodoApplication.kt`
  - possibly modify: `app/src/main/java/nl/mpcjanssen/simpletask/Constants.kt`
- changes:
  - when a notification target belongs to another file, switch active file first, then continue through current open/edit flow
  - make sure post-switch load timing is safe before launching edit
  - preserve current pinned-record retargeting after task text edits
- dependencies:
  - unit 2 resolver
  - unit 5 manager lifecycle
- risks:
  - visible file switching can feel surprising if not handled consistently
  - racing file switch vs editor launch can create flaky open/edit behavior
- tests:
  - manual validation only is likely realistic here
- acceptance:
  - tapping a scheduled-origin notification still opens the right task, even if another file was active beforehand

### 8. tighten done/unpin behavior for scheduled-origin notifications
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/MarkTaskDone.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/UnpinTaskNotification.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
- changes:
  - ensure `Done` and `Unpin` use the same resolver and state model whether the notification was posted immediately or by alarm
  - if a task is completed or removed before the trigger time, cancel the scheduled alarm and delete the record
  - keep all DB work off the main thread
- dependencies:
  - units 2–5
- risks:
  - it is easy to forget pre-trigger cleanup paths and leave stale alarms behind
- tests:
  - helper tests where practical
  - manual regression for notification actions
- acceptance:
  - scheduled pins cannot survive as orphaned alarms after unpin/completion

### 9. add targeted regression coverage and manual verification checklist
- files:
  - create/modify tests under `app/src/test/java/nl/mpcjanssen/simpletask/notifications/`
- changes:
  - add pure JVM coverage for:
    - delivery-state logic
    - request-code derivation
    - schedule-time normalization
    - resolver behavior for active vs non-active file
    - pre-trigger retarget/cancel decisions
  - define manual checks for:
    - immediate pin via default `now`
    - future scheduled pin
    - past-time immediate post fallback
    - reboot before trigger
    - unpin before trigger
    - complete before trigger
    - text edit before trigger
    - switch active file before trigger
- dependencies:
  - all previous units
- risks:
  - without explicit manual verification, alarm/boot/file-switch bugs are likely to slip through
- tests:
  - `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskKeyTest.kt`
  - `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskDeliveryStateTest.kt`
  - `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskTaskResolverTest.kt`
  - `app/src/test/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskAlarmSchedulerTest.kt`
- acceptance:
  - the feature has both helper-level regression coverage and explicit device checks

## sequencing
1. formalize delivery state in the pinned-record model
2. extract explicit-file task resolution and migrate manager lookups to it
3. add alarm scheduling helper with stable request-code derivation
4. add alarm receiver and boot/package restore handling
5. refactor manager into explicit schedule/post/unpin lifecycle paths
6. add scheduling UI in `Simpletask.kt` with default `now` behavior
7. wire switched-file open/edit behavior for notification taps
8. tighten done/unpin cleanup for pre-trigger and post-trigger cases
9. finish regression tests, manual verification, and build validation

## risks_and_unknowns
- risk: active-file coupling still leaks into one of the edit/open/done paths
  - mitigation: route every notification-origin action through one resolver instead of ad hoc file checks
- risk: duplicate identical tasks in the same file remain ambiguous
  - mitigation: keep key generation centralized and document this as a known v1 limitation
- risk: exact-alarm restrictions vary by device/vendor
  - mitigation: isolate scheduling policy in a helper and fall back gracefully when exact alarms are unavailable
- risk: visible file switching may feel jarring
  - mitigation: keep that behavior explicit in manual testing and avoid hidden partial cross-file edit logic in v1
- risk: boot restore or package replace can double-post
  - mitigation: use explicit delivery state and avoid treating all records as immediately postable

## validation_strategy
### focused JVM tests
Run the narrowest relevant tests first as new classes land:

```bash
JAVA_HOME="$HOME/.local/jdks/temurin-11" PATH="$HOME/.local/jdks/temurin-11/bin:$PATH" \
./gradlew :app:testCloudlessDebugUnitTest --tests nl.mpcjanssen.simpletask.notifications.PinnedTaskKeyTest \
  --tests nl.mpcjanssen.simpletask.notifications.PinnedTaskDeliveryStateTest \
  --tests nl.mpcjanssen.simpletask.notifications.PinnedTaskTaskResolverTest \
  --tests nl.mpcjanssen.simpletask.notifications.PinnedTaskAlarmSchedulerTest
```

### build verification
```bash
./scripts/build_cloudless.sh
```

### manual verification
- pin a task and accept default `now`; verify immediate post
- choose a time 2–3 minutes in the future; verify delayed post
- choose a time that is already in the past; verify immediate post
- unpin before trigger; verify no notification appears
- complete task before trigger; verify no notification appears
- edit task text before trigger; verify notification uses updated text
- schedule from file A, switch to file B, wait for trigger, and verify file A task still resolves
- reboot before trigger and verify alarm restoration
- tap notification for a task in a non-active file and verify the app switches file and opens the correct task

## handoff_notes
- do not start by adding the time picker; start by fixing state and resolver architecture
- prefer small new helpers over expanding `PinnedTaskNotificationManager.kt` into a giant state machine
- keep tests JVM-safe; avoid Android JSON methods and Android-app-singleton initialization in pure unit tests
- for user-facing code changes, remember to update `CHANGELOG.md` in the same commit
- if implementation pressure forces a tradeoff, prioritize correctness of schedule/cancel/open/done behavior over polish in the `Manage notification` UI

## execution_options
- implement directly in this session using this plan
- or hand this plan to a coding agent workflow for guarded execution
