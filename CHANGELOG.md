# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Favorite todo file switcher for quick navigation between frequently-used files
  - Add current file to favorites list from overflow menu
  - Open dedicated switcher dialog with alphabetically-sorted favorites
  - Favorites marked with visual indicator for currently active file
  - Remove unwanted favorites directly from switcher
  - Support for same-named files in different directories
- Close button in calendar view for returning to task list

### Fixed
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
