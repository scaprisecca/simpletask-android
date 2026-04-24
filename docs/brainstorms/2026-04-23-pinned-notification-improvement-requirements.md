# pinned notification improvement requirements

created: 2026-04-23
status: draft
source: ce-plan

## problem
- current pinned notifications exist, but the desired behavior is stronger persistence in the Android notification tray until the user explicitly resolves the task workflow
- Scott wants to start from the existing pinned notification system rather than building full per-task reminders first
- longer term, the feature should grow toward auto-triggered pinned notifications for due dates and optional user-chosen trigger times without cluttering todo.txt task lines

## goals
- improve the existing pinned notification flow into a more durable tray-based execution aid
- make pinned notifications persist until the user marks the task done or explicitly unpins it
- keep v1 manual-first while leaving room for future scheduled or due-date-triggered pinning

## non_goals
- full reminder engine in v1
- storing long reminder timestamps directly in todo.txt task text
- rule-based auto-pinning in the initial user-facing release

## proposed_behavior
- v1 pin creation remains manual
- v1 notification model is single-task only
- preferred behavior: pinned notifications should be non-dismissible if Android allows
- fallback behavior: if the system allows swipe dismissal, the notification should come back until the task is unpinned or completed
- long-term direction: allow auto-triggering from due dates and allow an explicit time for a pinned notification to appear
- grouped or multi-task notification support is explicitly deferred, but the persistence model should not block it later

## constraints
- todo.txt file should remain clean and not gain long timestamp clutter
- v1 pinned state is app-local on this phone and is not stored in task text
- Android notification behavior varies by OS version and vendor, so the plan must account for fallback behavior
- the repo already has a pinned notification path and should adapt existing patterns where possible

## open_questions
- v1 should support explicit unpinning both from the notification itself and from the in-app task UI
- grouped or multi-task pinning is deferred; v1 uses one notification per pinned task
- v1 notification affordances should include open/edit on tap plus explicit `done` and `unpin` actions
- how should pinned notifications behave when task text changes, task file changes, or the app reloads
- what persistence model should be used so scheduled pinning can be added later without a rewrite

## success_criteria
- the user can pin a task and reliably keep it present in the tray until done or explicitly unpinned
- notification behavior is predictable even if swipe-to-dismiss cannot be fully blocked on some devices
- pinned state survives app reloads, app restarts, and device reboot
- pinned notifications live-update to reflect task text changes
- if a pinned task is completed anywhere in the app workflow or through synced file changes, its notification is removed automatically
- the architecture remains compatible with future due-date-triggered or time-triggered pinning

## notes_for_planning
- existing relevant code paths include `Simpletask.kt`, `TodoApplication.kt`, and `MarkTaskDone.kt`
- existing broader ideation context is in `docs/ideation/2026_04_21_simpletasks_workflow_ideation.md`

## future_note
- if the app later needs a durable task identifier for features like reminders or stronger pinned-notification reconciliation, a `key:value` style task extension is an acceptable future direction to consider
- this is intentionally not part of the current implementation plan
