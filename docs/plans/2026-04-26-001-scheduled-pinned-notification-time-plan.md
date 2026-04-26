---
title: feat: scheduled pinned notification time
type: feature
status: active
date: 2026-04-26
origin: chat
source_doc: docs/brainstorms/2026-04-23-pinned-notification-improvement-requirements.md
supersedes:
  - docs/plans/2026-04-23-001-pinned-notification-persistence-plan.md
---

# feat: scheduled pinned notification time

> **For Hermes:** This is a draft implementation plan for the next step after persistent pinned notifications. Do not finalize implementation until the product questions in `open_product_questions` are answered.

## objective
- let a user choose a specific future time for a pinned notification to appear
- keep the reminder state app-local and out of todo.txt task text
- extend the current pinned notification system instead of building a separate reminder engine
- preserve a path toward later due-date-triggered or rule-triggered pin scheduling

## scope
### included
- manual scheduling of a pinned notification for one selected task
- app-local persistence of the scheduled trigger time
- Android alarm scheduling and reboot/package-replaced restoration
- notification posting at the selected future time
- cancel/reschedule behavior when the task is edited, completed, deleted, or unpinned before trigger time
- file-level architecture changes needed so the reminder can still resolve the task when a different todo file is active

### excluded
- bulk scheduling for multiple selected tasks
- rule-based auto-pinning from due dates in this pass
- cross-device sync of reminder state
- storing reminder timestamps in todo.txt lines
- a full generalized reminders screen unless needed for a minimal manage/edit flow

## key_codebase_findings
- `PinnedTaskRecord` already has `triggerAtMillis` and `triggerMode` fields in `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskRecord.kt`, but the current manager ignores them and always posts immediately.
- `PinnedTaskNotificationManager` is still built around the *currently active* todo file via `currentTodoFilePath()` and `TodoApplication.todoList.allTasks()`. That is sufficient for immediate in-app pinning, but it is not sufficient for a future alarm that may fire while a different file is active.
- `completeTaskFromNotification(...)`, `findTaskForPinnedKey(...)`, `isPinned(...)`, and task-edit retargeting all currently depend on active-file state, so exact-time scheduling is blocked by task-resolution architecture more than by notification UI.
- the app already has `AlarmManager` usage in `TodoApplication.kt` for daily refresh and periodic reload, so there is an established place to add app-owned scheduling.
- the app already has a custom `TimePreference` and existing date-picker patterns, so time selection UI can reuse familiar code paths.
- build config is currently `targetSdkVersion 31`, so Android 12 exact-alarm behavior matters if we promise precise trigger times.
- `Room.databaseBuilder(...).fallbackToDestructiveMigration()` is still enabled in `TodoApplication.kt`, so further schema changes are cheap technically but can wipe app-local state if versioning is mishandled.

## architecture_recommendation
### 1. treat scheduled pins as a state of the same pinned-record model
Do not create a separate reminder table unless file-level identity work forces it. Use `PinnedTaskRecord` as the source of truth for both:
- active/immediate pinned notifications
- future scheduled pins that have not fired yet

Recommended state model:
- `triggerMode = "immediate" | "scheduled_once"`
- `triggerAtMillis = null` for immediate pins, or a UTC epoch millis for scheduled pins
- add one more explicit status field if needed, such as `deliveryState = "scheduled" | "posted"`

Without an explicit delivery state, the manager cannot distinguish:
- "this task is pinned and already posted"
- "this task is pinned but waiting for a future alarm"

### 2. decouple task resolution from the active todo file
This is the biggest missing piece.

Scheduled pins will be unreliable unless the notification layer can resolve a task record by its stored `todoFilePath` even when that file is not the app's currently active file.

Recommended direction:
- extract a small helper/repository that can load tasks from an explicit file path through `FileStore.loadTasksFromFile(file)`
- centralize matching of a `PinnedTaskRecord` to a concrete task for:
  - alarm fire
  - notification tap/edit
  - `Done`
  - `Unpin`
  - reconcile after app reload, boot, sync, or task edits
- keep the current `TodoApplication.todoList` path as an optimization when the stored file matches the active file, but do not make it the only resolution path

### 3. make scheduling the default entry flow from the existing pin action
The lowest-risk UX is still to keep the current action location in the task context toolbar/menu, but the interaction should now go straight into scheduling instead of branching through a chooser.

Locked default interaction:
- if task is unpinned:
  - tap `Pin` -> open date/time scheduling flow
  - prefill date/time to `now`
  - if the user confirms without changing it, treat that as an immediate pin
- if task is already scheduled or posted:
  - same action becomes `Manage notification`

This keeps one primary path while still preserving immediate pinning as the zero-edit confirmation case.

### 4. use AlarmManager for trigger delivery, but design for graceful degradation
For a user-selected specific time, `AlarmManager` is the right primitive, not periodic polling.

Recommended behavior:
- use `setExactAndAllowWhileIdle(...)` when permitted
- if exact alarms are not available on the device/OS policy, fall back to `setAndAllowWhileIdle(...)` and communicate that delivery may be approximate
- add boot/package-replaced rescheduling from persisted DB records

This keeps the architecture correct even if the exactness policy needs refinement later.

## implementation_units

### 1. formalize scheduled-pin state in the persistence model
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskRecord.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/dao/Daos.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/TodoApplication.kt`
  - create: a small state helper under `app/src/main/java/nl/mpcjanssen/simpletask/notifications/`
- changes:
  - convert the currently-unused `triggerAtMillis` / `triggerMode` fields into explicit active behavior
  - likely add a `deliveryState` or equivalent helper so scheduled-vs-posted is not inferred indirectly
  - bump DB schema version
  - keep all state app-local
- risks:
  - if state is inferred only from nullability, edge cases around reschedule/unpin/post-after-boot become brittle
- tests:
  - pure JVM tests for state transitions and stored-value parsing/formatting
- acceptance:
  - record structure can represent immediate pin, future scheduled pin, and posted pin clearly

### 2. extract file-scoped task resolution independent of current app state
- files:
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskTaskResolver.kt` or similar
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/MarkTaskDone.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`
- changes:
  - add a resolver that can:
    - use active in-memory list when file matches active file
    - otherwise load explicit file contents through `FileStore.loadTasksFromFile(...)`
    - find the best matching task for a record
  - migrate notification actions away from `currentTodoFilePath()` assumptions
  - decide how edit-from-notification works when the pinned task belongs to a non-active file
- risks:
  - this is where duplicate identical task text remains fragile
  - notification actions against a non-active file may require opening/switching context or using an explicit target-file edit path
- tests:
  - pure helper tests with synthetic todo lines across same-file and different-file scenarios
- acceptance:
  - a scheduled pin can still fire and resolve the correct file even if the user has another file open when the alarm triggers

### 3. add alarm scheduling, cancellation, and reboot restore
- files:
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/PinnedTaskAlarmReceiver.kt`
  - modify: `app/src/main/AndroidManifest.xml`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/PinnedNotificationBootReceiver.kt`
  - possibly modify: `app/src/main/java/nl/mpcjanssen/simpletask/Constants.kt`
- changes:
  - add receiver for scheduled alarm delivery
  - schedule one alarm per scheduled record
  - cancel/replace alarms on unpin, reschedule, task completion, or task deletion
  - on boot/package replaced, reload scheduled records and re-register alarms
  - when an alarm fires, verify the record still exists and the task still resolves before posting
- risks:
  - duplicate delivery if alarm scheduling and immediate posting paths are not clearly separated
  - Android idle/exact-alarm restrictions vary by version/vendor
- tests:
  - pure tests for alarm request-code derivation and schedule-decision helpers
  - manual device validation for boot, package replace, and approximate-vs-exact delivery
- acceptance:
  - scheduled pins survive reboot and trigger once at the chosen time

### 4. add scheduling UI on top of the existing pin entry point
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
  - modify: `app/src/main/res/menu/task_context.xml`
  - modify: `app/src/main/res/values/strings.xml`
  - create: dialog layout/resources only if needed
- changes:
  - when a task is unpinned, open a date picker + time picker flow directly from the current pin action
  - default the chosen value to `now`
  - if confirmation time is already in the past, post immediately
  - if the task already has a scheduled pin, the action label should be `Manage notification`
- risks:
  - too much UI ambition here will slow down the feature; keep it to dialogs, not a new screen, unless editing/remanagement gets messy
- tests:
  - manual UI checks
  - pure tests for any helper that normalizes selected local date/time into epoch millis
- acceptance:
  - a user can schedule a task for a future pinned notification from the existing task action flow

### 5. define pre-trigger lifecycle behavior
- files:
  - modify: `PinnedTaskNotificationManager.kt` and resolver helpers
  - possibly modify: `AddTask.kt`
- changes:
  - if the task text changes before trigger time, retarget the scheduled record just like current pinned edit retargeting
  - if the task is completed before trigger time, remove the record and cancel the alarm
  - if the task disappears from sync/file edits before trigger time, remove the record and cancel the alarm on next reconcile
  - if the task's file path changes through app workflow, decide whether to retarget or cancel
- risks:
  - current edit retargeting only works for the active file path and same-session text edits
- tests:
  - unit tests for record-retargeting and cancellation conditions
- acceptance:
  - scheduled pins do not fire for deleted/completed tasks and follow straightforward edits predictably

### 6. tighten notification action behavior for scheduled-origin notifications
- files:
  - modify: `MarkTaskDone.kt`
  - modify: `UnpinTaskNotification.kt`
  - modify: `AddTask.kt`
  - modify: `PinnedTaskNotificationManager.kt`
- changes:
  - make sure `Done` and `Unpin` work the same whether the notification was pinned immediately or posted by a future alarm
  - define edit behavior when the task belongs to a non-active file:
    - switch the app to that file first, then continue through the existing edit/open flow
- risks:
  - switching files from a notification tap is simpler technically, but it changes visible app context and should be called out in manual testing
- tests:
  - manual regression checks for open/edit, done, and unpin
- acceptance:
  - scheduled-origin notifications behave exactly like immediate pinned notifications once posted

### 7. validation and regression coverage
- files:
  - create/modify tests under `app/src/test/java/nl/mpcjanssen/simpletask/notifications/`
- tests to add:
  - scheduled state parsing/defaults
  - epoch-time normalization helper behavior
  - record retargeting when text changes before trigger
  - alarm scheduling helper request-code stability
  - resolver behavior for active-file vs non-active-file cases
- manual checks:
  - schedule a task 2-3 minutes in the future and verify delivery
  - reboot before trigger and verify delivery still happens
  - unpin before trigger and verify no notification appears
  - complete task before trigger and verify no notification appears
  - edit task text before trigger and verify the notification uses the updated text
  - schedule a task from file A, switch active file to file B, wait for trigger, and verify file-A task still resolves correctly

## recommended_v1_defaults
- keep this feature manual-first: no due-date automation yet
- store all schedule state locally in Room
- reuse the current pin action and open the scheduling flow directly instead of showing a `Pin now` / `Pin at time` chooser
- default the date/time selection to `now`, so immediate pinning is just confirm-without-edit
- support one scheduled pin per task record
- use dialogs for date/time picking
- use `Manage notification` as the in-app label for already-scheduled or already-posted pins
- if the selected time is already in the past at confirmation, post immediately
- for notification tap/edit on another todo file, switch to that file first because it is the simpler technical path with the current codebase
- allow unpin from the same place regardless of whether the pin is pending or already posted
- for v1, `unpin and create again` is acceptable instead of true rescheduling UI
- if exact alarms are unavailable, still schedule with best-effort delivery rather than blocking the feature outright

## product_decision_rationale
- default-to-scheduling removes an extra chooser step while preserving immediate pinning as the zero-edit confirmation case
- `Manage notification` fits both scheduled and already-posted states better than `Unpin` or `Reschedule` as the single toolbar label
- switching to the target file first is the easier implementation path because current pinned-task resolution, editing, and completion flows are strongly active-file-centric
- allowing `unpin and create again` keeps v1 smaller and avoids extra schedule-edit UI before the core alarm lifecycle is proven

## major_risks_and_unknowns
- **active-file coupling is the main architectural gap.** Without fixing that, a future trigger is only reliable when the same todo file is still active.
- **duplicate identical task text remains ambiguous.** Scheduled delivery makes that limitation more visible because time passes between scheduling and resolution.
- **exact-alarm behavior on Android 12+ is nuanced.** We can build the scheduling architecture now, but UX promises about “exact” delivery should be modest.
- **edit-from-notification for non-active files will change visible app context.** The chosen v1 approach is to switch to the reminder's file first because it is technically simpler, but that behavior needs explicit manual validation.

## remaining_open_questions
- should `Manage notification` in v1 show only an `Unpin` action, or also show read-only schedule details like the chosen trigger time?
- after switching to another todo file from a scheduled notification tap, should the app keep the user on that file or try to return them to the prior active file afterward? Current recommendation is to keep them on the reminder's file for simpler implementation.

## validation_strategy
- run pure JVM tests for new helpers first
- run the focused unit tests for notification helpers once they exist
- rebuild with `./scripts/build_cloudless.sh`
- manual device verification for alarm timing, reboot restore, file-switch behavior, edit flow, and action buttons

## handoff_notes
- do not treat this as “just add a time picker.” The real work is file-scoped task resolution and scheduled-state lifecycle.
- prefer extending the current pinned-notification architecture over introducing a second reminder system.
- keep the implementation local-first and avoid adding todo.txt metadata.
- if implementation starts before product answers arrive, use the defaults in `recommended_v1_defaults` and mark the unresolved behavior clearly in the PR/plan.
