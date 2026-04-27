---
title: scheduled pinned notification manual test checklist
type: manual-test-checklist
status: active
date: 2026-04-26
feature: scheduled pinned task notifications
related_docs:
  - docs/plans/2026-04-26-001-scheduled-pinned-notification-time-plan.md
  - docs/plans/2026-04-26-002-scheduled-pinned-notification-time-implementation-plan.md
---

# scheduled pinned notification manual test checklist

Use this checklist on a real Android device or emulator with notification permission enabled.

## setup
- install the latest debug APK
  - `app/build/outputs/apk/cloudless/debug/app-cloudless-debug.apk`
- open a todo file with at least 3 active tasks
- include:
  - one task in the currently active file
  - one task in a different todo file
  - two tasks with identical text in the same file for duplicate-key regression checks
- make sure the device clock/timezone are correct
- if the device is Android 12+, note whether exact alarms appear delayed or vendor-throttled

## recommended test data
- task A: `Review PR`
- task B: `Call vendor`
- task C: `Plan sprint`
- duplicate tasks: two separate `Buy batteries`

## test cases

### 1) immediate pin via default now
1. open the main task list
2. select a single task in the active file
3. tap the pin action
4. accept the default date/time without moving it meaningfully into the future
5. verify a notification appears immediately
6. verify the toolbar action label now reads `Manage notification`
7. verify the notification text matches the pinned task text

**expected**
- notification posts immediately
- no crash
- pinned state is reflected in the toolbar/menu

### 2) future scheduled pin
1. select a different task
2. tap the pin action
3. choose a time 2–3 minutes in the future
4. confirm the schedule
5. verify no notification appears immediately
6. wait until the chosen time passes
7. verify the notification appears once

**expected**
- task is treated as pinned before the notification posts
- notification appears at or near the scheduled time
- only one notification is posted

### 3) past-time fallback posts immediately
1. select another task
2. tap the pin action
3. choose a time a few minutes in the past
4. confirm
5. verify the notification posts immediately

**expected**
- no future alarm behavior remains
- the notification is visible right away

### 4) manage notification -> unpin from app UI
1. pin a task
2. reselect the same task
3. tap `Manage notification`
4. choose `Unpin`
5. verify the notification disappears or never posts if it was scheduled for the future

**expected**
- pinned state clears
- task is no longer marked as pinned
- no stale notification appears later

### 5) unpin from notification action
1. create an immediate pin
2. from the notification shade, tap `Unpin`
3. verify the notification disappears
4. reopen the app and verify the task is no longer pinned

**expected**
- unpin works from the notification itself
- no duplicate or re-posted notification returns afterward

### 6) done from notification action
1. create an immediate pin
2. tap `Done` from the notification
3. verify the correct task is completed
4. verify the notification disappears
5. verify the task list updates correctly in the app

**expected**
- the intended task is completed, not a different one
- no stale pinned record remains visible

### 7) edit task text before a future trigger
1. schedule a task 2–3 minutes in the future
2. before the trigger time, open and edit that task’s text
3. save the edit
4. wait for the trigger time
5. verify the notification appears with the updated task text

**expected**
- scheduled pin survives the text edit
- notification shows the updated text, not the old text

### 8) switch active file before future trigger
1. from todo file A, schedule a task 2–3 minutes in the future
2. switch the app to todo file B
3. wait for the trigger time
4. verify the notification for the file A task still appears

**expected**
- scheduling is not tied to whichever file is currently active
- notification resolves the original task from file A

### 9) open notification for a task in a non-active file
1. schedule a task in todo file A
2. switch the app to todo file B before the trigger
3. wait for the notification to appear
4. tap the notification body/open action
5. verify the app switches back to todo file A and opens the correct task editor/view path

**expected**
- file switch happens automatically
- the correct task opens after switching files
- no wrong-file edit occurs

### 10) complete task before future trigger
1. schedule a task 2–3 minutes in the future
2. before the trigger time, complete the task from within the app
3. wait past the original trigger time
4. verify no notification appears

**expected**
- scheduled alarm is cleaned up
- completed tasks do not later post orphaned notifications

### 11) unpin task before future trigger
1. schedule a task 2–3 minutes in the future
2. before the trigger time, unpin it from the app
3. wait past the original trigger time
4. verify no notification appears

**expected**
- scheduled alarm is cancelled
- no stale post happens later

### 12) app restart restore
1. schedule one future notification and create one immediate/posted pin
2. fully close the app
3. reopen the app
4. verify:
   - posted pin is restored
   - future scheduled pin still fires at the expected time

**expected**
- restart does not drop either scheduled or posted state

### 13) reboot restore
1. schedule one future notification and create one immediate/posted pin
2. reboot the device before the future trigger time
3. after boot, wait for app restore to settle
4. verify the posted notification is restored
5. verify the scheduled one still fires once

**expected**
- boot/package restore logic restores both record types correctly
- no double-posting occurs

### 14) duplicate text regression
1. in the same todo file, create two tasks with exactly the same text, for example `Buy batteries`
2. pin one of them
3. test:
   - unpin from app
   - immediate notification post
   - done from notification
   - future schedule then edit one duplicate
4. verify whether the correct duplicate is affected each time

**expected**
- note any mismatch carefully
- this is a known weak area because keying still depends on file path + task text

## failure notes to capture
If anything fails, record:
- device model
- Android version
- whether battery optimization is enabled
- whether notification permission is granted
- whether exact alarms appear restricted/delayed
- which test case failed
- exact observed behavior
- whether the task was in the active file or another file
- whether duplicate task text was involved

## pass criteria
Call the feature good enough for v1 if all of these hold:
- immediate pins post reliably
- future pins fire once at roughly the expected time
- unpin/done cleanup prevents stale future notifications
- cross-file notification open/edit behavior works
- restart/reboot restoration works
- no crashes occur during schedule, trigger, open, done, or unpin flows

## known limitation to keep in mind
Duplicate identical task text in the same file may still be ambiguous because the pinned-record key is based on canonical todo file path + task text.