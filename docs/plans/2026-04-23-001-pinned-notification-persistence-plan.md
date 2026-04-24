---
title: feat: persistent pinned task notifications
type: feature
status: active
date: 2026-04-23
origin: chat
source_doc: docs/brainstorms/2026-04-23-pinned-notification-improvement-requirements.md
---

# feat: persistent pinned task notifications

> **For Hermes:** This plan is manual-first for v1. Keep the storage/model choices compatible with later scheduled pinning from due dates and explicit trigger times, but do not build scheduling yet.

## objective
- upgrade the current experimental pinned notification behavior into a durable, app-owned pinned-task system
- keep one notification per pinned task in v1
- keep pinned state app-local on this phone only
- make the notification stay present until the task is completed or explicitly unpinned
- make pinned state survive app reloads, app restarts, and device reboot
- ensure pinned notifications live-update when task text changes and auto-remove when the task is completed anywhere

## scope
### included
- manual pin/unpin flow for single tasks
- persistent app-local storage for pinned state
- notification action set: tap to open/edit, `Done`, `Unpin`
- automatic restoration after app startup and device reboot
- fallback re-post behavior if swipe dismissal cannot be fully prevented
- in-app pin/unpin affordance using the existing task context menu path
- live notification refresh on task text changes and automatic removal on completion

### excluded
- scheduled pinning UI or scheduling engine
- automatic due-date-triggered pinning
- grouped or multi-task pinned notifications
- cross-device synced pin state
- storing pin state or trigger times in todo.txt task text

## product decisions captured
- v1 pin creation remains manual, but the model should not block future scheduled pinning
- v1 is single-task only
- preferred behavior is non-dismissible if Android allows; fallback is automatic reappearance until unpinned or completed
- unpin should be available both from the notification and from the app UI
- tapping the notification opens/edit the task
- pinned notifications should live-update when task text changes
- completion anywhere should automatically remove the notification
- pinned state must survive reloads, restarts, and reboot
- pinned state is app-local only

## relevant_existing_patterns
- `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
  - current `pinNotification(checkedTasks)` entry point and notification construction
- `app/src/main/res/menu/task_context.xml`
  - current `pin_notification` task action location
- `app/src/main/java/nl/mpcjanssen/simpletask/TodoApplication.kt`
  - app startup, notification channel creation, `updatePinnedNotifications()`, local broadcast reactions
- `app/src/main/java/nl/mpcjanssen/simpletask/MarkTaskDone.kt`
  - current notification `Done` service path; currently too weak because it marks complete in memory and cancels notification, but does not appear to save through the normal persistence path
- `app/src/main/java/nl/mpcjanssen/simpletask/task/TodoList.kt`
  - canonical task mutation/save/reload flow and task lookup by in-memory `id`
- `app/src/main/java/nl/mpcjanssen/simpletask/task/Task.kt`
  - current task identity reality: in-memory `id` is not durable across reloads; `uuid:` support exists but is intentionally out of scope for v1 pin persistence
- `app/src/main/java/nl/mpcjanssen/simpletask/Constants.kt`
  - existing notification and alarm extras/constants
- `app/src/main/AndroidManifest.xml`
  - no current boot receiver permission/path for restoring pinned notifications after reboot
- `app/src/main/java/nl/mpcjanssen/simpletask/dao/Daos.kt`
  - existing Room database is available, but currently built with `fallbackToDestructiveMigration()` in `TodoApplication.kt`, so new persisted state should be added carefully

## key architecture decision
### use persistent app-local pinned records, not active-notification introspection, as source of truth
The current implementation effectively treats active notifications as the state source and uses `getActiveNotifications()` to refresh text. That is insufficient for the new requirements because active notifications do not survive reboot reliably as the app's authoritative source, and swipe dismissal can erase state.

For v1, the source of truth should be a small persisted local record such as:
- `taskKey`
- `todoFilePath`
- `createdAt`
- optional `lastKnownText`
- optional future fields reserved for scheduling (`triggerAtMillis`, `triggerMode`) but unused in v1

### task key strategy for v1
Because pinned state is app-local only, use a local derived task key rather than writing anything into task text.

Recommended v1 key shape:
- canonical todo file path
- current raw task line text as persisted in file format

Important limitation:
- this is not perfect for duplicate identical tasks in the same file
- however, it matches the current product direction better than adding file metadata now
- the implementation should centralize task-key generation so it can later be replaced by `uuid:` or another durable identity if scheduled pinning expands

### expected v1 tradeoff
This means duplicate identical tasks in the same file may be ambiguous for pin restoration/removal. That is acceptable for v1 only if the limitation is documented in the plan and the matching logic is centralized for future replacement.

## implementation_units

### 1. define persistent pinned-task model and storage
- files:
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskRecord.kt`
  - create or extend: Room DAO/entity file under `app/src/main/java/nl/mpcjanssen/simpletask/dao/`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/dao/Daos.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/TodoApplication.kt`
- changes:
  - add a new Room entity/table for pinned task records
  - bump schema version
  - add DAO methods for insert/upsert/delete/list
  - avoid building logic around active notifications as the only state source
  - encapsulate task-key generation in one helper
- dependencies:
  - existing Room DB in `TodoApplication`
- risks:
  - repo currently uses `fallbackToDestructiveMigration()`; schema changes can wipe app-local data on migration failures or version churn
  - duplicate identical task lines are ambiguous with app-local text-based matching
- tests:
  - pure/JVM tests for task-key helper if extracted
  - DAO-level coverage only if existing test setup makes that practical; otherwise document as manual validation
- acceptance:
  - app can persist pinned records independent of active notifications
  - a central helper exists for mapping between a task and its app-local pinned identity

### 2. extract notification orchestration into a dedicated manager
- files:
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/notifications/PinnedTaskNotificationManager.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/TodoApplication.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Constants.kt`
- changes:
  - move notification construction/post/cancel/refresh logic out of `Simpletask.pinNotification(...)` and `TodoApplication.updatePinnedNotifications()` into one manager
  - manager responsibilities:
    - create/update a single-task notification
    - cancel notification by pinned record
    - rebuild notifications from persisted records
    - reconcile persisted records against current task list
  - create a dedicated channel contract for pinned task notifications; reuse existing channel ID if migration cost is lower, or rename carefully if needed
  - add intent extras/constants for unpin action routing if needed
- dependencies:
  - new pinned record storage from Unit 1
- risks:
  - if the manager relies too much on UI-layer `Task.id`, reload/restart behavior will still be wrong
- tests:
  - focused unit tests for notification model builder helper if extracted
- acceptance:
  - one place owns pinned notification behavior
  - notification posting and restoration no longer depend on `Simpletask` activity state

### 3. fix action flows: `Done`, `Unpin`, and open/edit
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/MarkTaskDone.kt`
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/UnpinTaskNotification.kt` or similar service/receiver
  - modify: `app/src/main/AndroidManifest.xml`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
- changes:
  - `Done` action must use the normal task persistence path, not only in-memory mutation
  - after completion, remove the pinned record and cancel the notification
  - add explicit `Unpin` action path that removes pinned record and cancels notification without modifying the task itself
  - keep tap-to-open/edit behavior via `AddTask`
- dependencies:
  - new pinned record storage and notification manager
- risks:
  - current `MarkTaskDone` path appears incomplete; patching it wrong could regress task completion/save behavior
- tests:
  - manual verification is essential here
  - add narrow unit coverage for helper logic where possible
- acceptance:
  - tapping `Done` completes and saves the task, then removes notification
  - tapping `Unpin` removes notification and pinned state only
  - tapping the body opens the task editor

### 4. add in-app pin/unpin state handling to existing task UI
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
  - modify: `app/src/main/res/menu/task_context.xml`
  - modify: `app/src/main/res/values/strings.xml`
- changes:
  - for single selected task, the context action should reflect current state:
    - `Pin as notification` when unpinned
    - `Unpin notification` when pinned
  - because v1 is single-task only, disable or hide this action for multi-select rather than preserving the current bulk flow
  - ensure selection and menu invalidation react correctly after pin/unpin
- dependencies:
  - ability to query pinned state quickly from local storage/cache
- risks:
  - mixing old multi-select assumptions with single-task v1 could create odd UX states
- tests:
  - manual checks around single select vs multi-select behavior
- acceptance:
  - user can pin/unpin explicitly from the app UI
  - app UI reflects pinned state clearly

### 5. reconcile pinned notifications on reload, sync, and edits
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/TodoApplication.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/task/TodoList.kt`
  - possibly modify: `app/src/main/java/nl/mpcjanssen/simpletask/AddTask.kt`
- changes:
  - on task-list changed broadcasts, reconcile persisted pinned records against the current task set
  - if the task text changed but still matches the pinned record through the chosen key strategy, update notification text
  - if the task is completed or no longer resolvable, remove the notification and pinned record
  - stop relying on `notificationManager.getActiveNotifications()` as the primary refresh loop
- dependencies:
  - Units 1 and 2
- risks:
  - if matching logic is too naive, edited tasks may incorrectly lose pinned state
  - with duplicate identical tasks, reconciliation may target the wrong task
- tests:
  - helper tests around record-to-task reconciliation if extracted
- acceptance:
  - pinned notifications update after normal task edits
  - pinned notifications disappear when task completion arrives through any save/reload path

### 6. restore pinned notifications after app startup and reboot
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/TodoApplication.kt`
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/PinnedNotificationBootReceiver.kt`
  - modify: `app/src/main/AndroidManifest.xml`
- changes:
  - restore pinned notifications from persisted records during app startup after todo list is loaded enough to reconcile
  - add boot receiver and manifest wiring for `BOOT_COMPLETED`
  - add required permission if needed
  - on boot, rehydrate notifications after app-local state is available; if direct file reconciliation at boot is too early, hand off to application startup logic safely
- dependencies:
  - persisted pinned records and manager
- risks:
  - boot timing/file availability can be tricky, especially with synced files
  - Android background restrictions may limit what is safe directly in the boot receiver; use minimal receiver logic and delegate quickly
- tests:
  - manual verification via reboot/emulator restart path
- acceptance:
  - pinned notifications are restored after reboot for still-valid tasks

### 7. implement non-dismissible preference path with safe fallback
- files:
  - modify: notification-building code in new manager
  - possibly modify: strings/help text if behavior needs explanation
- changes:
  - configure notification flags/properties for strongest practical persistence supported by the target Android APIs
  - if true non-dismissible behavior is not achievable consistently, implement the planned fallback: dismissed notifications are re-posted during reconciliation as long as the pinned record still exists and the task is incomplete
  - be careful not to create an aggressive loop that feels buggy or hostile
- dependencies:
  - manager + persisted state + reconciliation cycle
- risks:
  - Android may not allow hard non-dismissible behavior outside foreground-service/ongoing-notification patterns
  - using foreground-service semantics just to force persistence would be too heavy for v1
- tests:
  - manual behavior checks on at least one current Android version
- acceptance:
  - behavior is as persistent as practical
  - fallback reappearance works without user confusion or notification spam

### 8. reserve extension points for future scheduled pinning without implementing it
- files:
  - new manager/storage helpers from earlier units
  - plan comments/docs only where needed
- changes:
  - keep pinned record schema and manager API friendly to later fields such as trigger time and trigger mode
  - do not expose scheduling UI or logic yet
  - document the future insertion point for due-date-triggered or explicit-time-triggered pinning
- dependencies:
  - Units 1 and 2
- risks:
  - over-engineering for future scheduling would bloat v1
- tests:
  - none beyond keeping APIs simple
- acceptance:
  - v1 code does not need a rewrite just to add scheduled pin creation later

## sequencing
1. create pinned record storage and central task-key helper
2. extract a dedicated pinned notification manager
3. fix `Done` persistence path and add explicit `Unpin` action path
4. convert app UI to single-task pin/unpin affordance
5. reconcile pinned state on edits/reloads/sync
6. add startup restoration and boot restoration
7. harden persistence behavior and dismissal fallback
8. document future scheduling insertion points

## risks_and_unknowns
- **largest correctness risk:** current task identity is not durable across reloads because `Task.id` is in-memory only
  - mitigation: centralize app-local task-key generation now; keep the matching logic replaceable
- **duplicate identical tasks in one file:** app-local text-based matching can be ambiguous
  - mitigation: accept as a v1 limitation, document it, and avoid scattering matching logic
- **`MarkTaskDone` currently appears underpowered:** it completes in memory and cancels the notification without clearly saving through the canonical path
  - mitigation: explicitly route notification completion through the normal save flow and manual-test it first
- **boot behavior on Android can be finicky**
  - mitigation: keep boot receiver thin and delegate restoration into application logic
- **notification non-dismissible behavior may not be fully enforceable**
  - mitigation: implement the preferred strongest persistence flags, then rely on persisted-state re-post fallback
- **Room migration fragility** because DB uses `fallbackToDestructiveMigration()`
  - mitigation: keep the new schema simple and consider whether destructive migration is acceptable for app-local pin state during development; if not, add a proper migration before shipping

## validation_strategy
### unit / focused tests
- task-key helper tests:
  - canonical file path + raw task text → deterministic key
  - edited task text changes key as expected under v1 model
- reconciliation helper tests:
  - pinned record + current task list → update/remove decision
- optional notification-builder helper tests for actions/extras

### manual checks
- pin a single task from the app UI → notification appears
- tap notification body → opens edit screen for that task
- tap `Done` → task completes, saves, and notification disappears
- tap `Unpin` → notification disappears, task remains active
- edit pinned task text in app → notification text updates
- complete pinned task in app, not from notification → notification disappears automatically
- reload/sync todo list → still-valid pinned notifications remain; invalid/completed ones are removed
- restart app → pinned notification restored
- reboot device/emulator → pinned notification restored
- if swipe dismissal is possible on the device, the notification reappears until unpinned or completed
- multi-select a set of tasks → pin action is hidden or disabled in v1

## acceptance summary
- v1 delivers one persistent notification per pinned task
- notification can be removed only by `Done` or `Unpin` from the intended product perspective
- behavior survives restart and reboot
- task text changes are reflected in the notification
- completion anywhere clears the pinned notification
- storage and manager design leave a clean insertion point for future due/time-triggered pinning

## handoff_notes
- do not treat active notifications as the source of truth; persisted pinned records must own the state
- fix the `MarkTaskDone` save path before trusting notification actions
- keep single-task scope tight; do not accidentally preserve bulk pinning semantics in the UI
- centralize task-key logic because it is the most likely thing to change in v2 when scheduled pinning or durable task identity grows
- if implementation reveals that app-local text-based matching is too fragile even for v1, stop and re-evaluate before shipping rather than quietly introducing wrong-task behavior
