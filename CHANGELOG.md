# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Pinned task notifications can now be scheduled for a specific date and time, survive reboot/app restore, and still resolve the right task even when it lives in another todo file
- Date lenses for All, Overdue, Today, This Week, and Upcoming are now available from the main task list and quick-filter drawer, with configurable week start and upcoming window settings
- Favorite todo files can now be pinned as dedicated quick-add shortcuts that open the normal Add Task screen and save new tasks directly into the selected list
- Favorite labels can now be added to favorite todo files to keep the favorites switcher and quick-add shortcut chooser easier to scan
- Favorite todo file switcher for quick navigation between frequently-used files
  - Add current file to favorites list from overflow menu
  - Open dedicated switcher dialog with alphabetically-sorted favorites
  - Favorites marked with visual indicator for currently active file
  - Remove unwanted favorites directly from switcher
  - Support for same-named files in different directories
- Close button in calendar view for returning to task list

### Fixed
- Pinned task notifications now restore more reliably after reboot and app updates, expose a dedicated Unpin action, and reappear after swipe-away instead of silently unpinning
- Pinned task notifications now survive app reloads and device reboot, stay in sync after task edits, and let you unpin or complete the correct task from the notification
- Pinned notifications now open safely from the notification shade, show visual indicators in the task list for posted vs scheduled pins, and more reliably restore/edit the correct task across files and duplicate task text
- Pinned task notifications now refresh in-app pin state immediately after pin or unpin actions, restore posted notifications after reboot without requiring an app open, and route notification taps through the correct todo-file switch before opening the editor
- Quick-add shortcuts now launch in their own capture flow so saving or cancelling returns cleanly instead of leaving the main app open unexpectedly
- File-specific quick-add sessions now load context/project suggestions from the shortcut target file instead of the currently active file
- File-specific quick-add now shows a warning and stays out of the way if another Add Task editor is already open
- Exiting calendar view with the header close button now fully restores normal task-list mode, including the correct title and task editing/selection toolbar behavior
- Add Task now moves the cursor to the end of the current task after inserting contexts, projects, due dates, and threshold dates, even when the task wraps across multiple on-screen lines
- Add Task now keeps the cursor on the active line when inserting contexts, projects, and priorities after the editor loses focus to the toolbar
- Add Task now preserves the caret position when inserting contexts, projects, and priorities, including in multi-line entries
- Build compilation errors preventing APK assembly
  - Removed unused anko-commons dependency
  - Corrected preference property modifier from val to var
  - Fixed visibility modifier conflict in Preferences base class
  - Added kapt configuration for proper annotation processing
  - Locale-aware string case conversion for compatibility

### Changed
- Favorite file switching now prompts to save or discard pending changes before switching files
- Unavailable favorites stay visible in switcher until explicitly removed by user

---
