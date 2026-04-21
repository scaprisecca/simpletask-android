# simpletasks workflow ideation

created: 2026-04-21
status: active
repo: simpletasks_app
focus: workflow improvements for power todo.txt users, especially vault + multi-file users

## context
- inspected repo guidance in `README.md`, `CONTRIBUTE.md`, and `AGENTS.md`
- reviewed main task model in `app/src/main/java/nl/mpcjanssen/simpletask/task/Task.kt` and filtering/query flow in `Query.kt`, `FilterActivity.kt`, and `QueryStore.kt`
- reviewed current app capabilities around favorites, quick add, widgets, pinned notifications, calendar mode, reminders, and file switching in `Simpletask.kt`, `TodoApplication.kt`, `AppWidgetService.kt`, `CalendarSync.kt`, and related calendar/fileswitch code
- reviewed Lua integration in `Interpreter.kt`, `AbstractInterpreter.kt`, `FilterScriptFragment.kt`, `ScriptConfigScreen.kt`, and `app/src/main/assets/script.en.md`
- noted recent unreleased work already adds favorite file switching, favorite labels, favorite quick-add shortcuts, and calendar mode fixes in `CHANGELOG.md`
- current codebase already supports: todo.txt core parsing, due/threshold/recurrence, saved filters, widgets, sharing/intents, pinned notifications, calendar reminders via Android calendar, a month calendar mode, and Lua callbacks for add/filter/group/sort/display/text search

## candidate_ideas
### 1. built-in date lenses: today, this_week, overdue, upcoming
- summary: add first-class quick filters and widget presets for common date horizons without requiring users to write Lua or manually rebuild queries.
- value: very high for Scott and for general users. This directly supports day-to-day execution and reduces friction around due and threshold handling.
- cost: medium. Mostly query/preset/UI work, possibly plus a small “smart filter” abstraction.
- risk: low to medium. Biggest risk is making built-ins overlap awkwardly with saved filters and calendar mode.
- why_it_might_fail: if implemented as hardcoded one-offs in the UI, it will feel brittle and become another settings maze.
- notes:
  - Scott already has `Set up a TODAY filter in simpletasks` and `Setup a THIS WEEK filter in simpletasks` in `tasks/todo.txt`
  - likely strongest near-term win because it solves a real current need and helps app-store users immediately
  - good scope split: P1 built-in chips/presets, P2 editable smart filters, P3 widget templates

### 2. smarter multi-file workflow: default file roles, recent files, file-aware widgets/shortcuts
- summary: turn the new favorites work into a real multi-file system with user-friendly roles like inbox, personal, workbench, maybe per-file default add target and recent-file memory.
- value: very high for Scott, medium-high for power users managing multiple todo.txt files with Syncthing, Obsidian, or separate life domains.
- cost: medium.
- risk: medium. File-target semantics can create data-loss risk if the active file and save target are unclear.
- why_it_might_fail: if the app blurs “active list” and “explicit save target,” users will lose trust fast.
- notes:
  - strongest product angle is clarity: always show where a task will be saved
  - possible features:
    - mark favorite files with roles like default quick add, default share target, archive pair
    - “last used target” option for quick add
    - recent files section above favorites
    - file badge in Add Task when launched from a shortcut
    - file-aware widgets pinned to a favorite file, not only the active file

### 3. agenda mode: date-first list view below calendar, not just month browsing
- summary: build a complementary agenda screen that groups tasks into overdue, today, tomorrow, this week, unscheduled, while still using the todo.txt model.
- value: high. This is closer to how many people actually work than a pure month grid, and it pairs well with Scott’s vault workflow.
- cost: medium-high.
- risk: medium. Could overlap with filters, widgets, and calendar mode unless framed clearly.
- why_it_might_fail: if it becomes an alternate everything-view with too many settings, it will add complexity without enough clarity.
- notes:
  - best framing is “agenda mode” as an execution view, while existing calendar mode remains a planning view
  - likely more broadly useful than expanding the month calendar itself
  - Lua could later customize section assignment or labels

### 4. first-class Lua recipe system with templates, examples, and reusable modules
- summary: make Lua a visible power-user extension surface instead of a hidden advanced tab by shipping script recipes, tested examples, and import/exportable modules.
- value: high for differentiation. This is the most unique part of the app and a credible way to serve both simple users and advanced users.
- cost: medium.
- risk: low to medium. Main risk is exposing Lua without enough guardrails, discoverability, or debugging.
- why_it_might_fail: if only raw code editors are exposed, most users still will not touch it.
- notes:
  - current Lua support is already substantial but underpackaged:
    - `onAdd`
    - `onFilter`
    - `onGroup`
    - `onSort`
    - `onDisplay`
    - `onTextSearch`
    - config overrides for theme and task list text size
  - high-value product improvements:
    - bundled scripts: today, this week, GTD next actions, waiting-for, someday/maybe, project grouping
    - recipe gallery with short explanations and test tasks
    - import from `.lua` file and save as named template
    - script validation with clearer error messages and performance warnings
    - module snippets for reusable helpers instead of one long config blob

### 5. task creation accelerators: metadata suggestions, reusable snippets, capture presets
- summary: improve Add Task into a better capture tool for structured todo.txt entry, especially for recurring metadata combinations.
- value: high for daily use.
- cost: medium.
- risk: low.
- why_it_might_fail: if it becomes a heavy form-builder, it will fight the speed advantage of plain text entry.
- notes:
  - current app already has context/project/date insertion helpers and prefill via filters/intents
  - promising additions:
    - saved capture presets like “call”, “errand”, “research”, “waiting for”
    - auto-suggest contexts/projects from active or selected target file
    - optional expansion snippets, e.g. typing `/waiting` inserts `@waiting due:+7d`
    - recent metadata chips based on last N tasks in the current file
  - Lua `onAdd` is a strong backend hook here for automatic normalization or enrichment

### 6. notification system overhaul: real reminders instead of only pinned notifications/calendar sync
- summary: add native task reminders/alerts that do not rely entirely on Android calendar sync, with better UX for overdue and scheduled tasks.
- value: medium-high.
- cost: medium-high.
- risk: medium-high because Android background behavior can get ugly fast.
- why_it_might_fail: if reliability is inconsistent across Android versions/vendors, users will blame the app.
- notes:
  - current state appears split between:
    - pinned notifications for selected tasks
    - experimental calendar reminders via Android calendar sync
    - periodic reload alarms for app refresh
  - user-visible opportunity is strong, but architecture needs care
  - better as a later feature unless Scott wants reminders urgently

## lua_extension_opportunities
### practical uses already supported by the code
- add-time normalization with `onAdd`
  - auto-append default tags or contexts for specific capture flows
  - translate shorthand like `!today` into `due:YYYY-MM-DD`
  - attach UUIDs, area tags, or custom metadata automatically
- smarter text search with `onTextSearch`
  - fuzzy search
  - token-aware search that matches only projects/contexts
  - special search operators like `is:overdue`, `has:due`, `area:fitness`
- dynamic display with `onDisplay`
  - cleaner task rendering for mobile by hiding noisy metadata and surfacing the key bits first
  - show computed labels like `OVERDUE`, `DUE TODAY`, or a shortened project path
- custom grouping and sorting with `onGroup` and `onSort`
  - group into `overdue`, `today`, `this week`, `later`, `someday`
  - sort by urgency score rather than raw alphabetical/date order
  - group by status derived from tags/extensions instead of raw todo.txt tokens
- custom workflow filters with `onFilter`
  - implement GTD-like next action screens
  - hide delegated items until due date
  - define reusable “clean room”, “deep work”, or “phone calls” views beyond stock filters

### stronger product ideas built on Lua
- recipe gallery: ship curated scripts as selectable templates from the filter screen
- named script modules: let users save Lua snippets as reusable building blocks and apply them across filters/widgets
- safe computed fields: expose helper APIs for common date windows so users do not need to write low-level Lua time logic
- file-aware scripting: pass active file metadata or favorite label into the Lua environment so behavior can change per list
- vault-friendly transforms: support note-link helper recipes, e.g. normalize `note:[[...]]` patterns or area/project conventions at capture time

### limitations to keep in mind
- current Lua runs in-process and often per task, so slow scripts can cause ANRs
- Lua is powerful but hidden; without examples and guardrails, most users will never discover its value
- debugging is currently basic: toast/log and a test UI exist, but there is no strong linting, profiling, or script catalog

## rejected_or_weaker_options
- expand month calendar into a full calendar app — too much ceremony, weak fit for todo.txt, and likely high maintenance
- full sync/account system redesign first — useful eventually, but not the best leverage for Scott’s current workflow or app-store differentiation right now
- rich markdown/notes editor inside tasks — fights the plain-text todo.txt model and risks turning the app into a worse notes app
- heavy collaboration/shared list features — high complexity and off-core for the current product direction
- kanban board mode — visually appealing, but less aligned with existing strengths than agenda, smart filters, or file workflows

## recommended_next_steps
1. built-in date lenses (`today`, `this_week`, `overdue`, `upcoming`) with optional widget support
2. agenda mode as a separate execution view after date lenses are defined
3. first-class Lua recipe system with bundled templates and better UX
4. smarter multi-file workflow on top of favorites and quick-add targets

## scott_priority_order
1. built-in date lenses (`today`, `this_week`, `overdue`, `upcoming`)
2. agenda mode
3. first-class Lua recipe system

## confirmed_decisions
- `today` should include:
  - overdue tasks
  - tasks due today
  - tasks whose threshold/start date is today
  - a task with threshold/start today should still appear in `today` even if its due date is later
- `this_week` should not include overdue tasks
- `this_week` should include:
  - tasks due this week
  - tasks whose threshold/start date is this week
  - tasks must have a `due:` date or `t:`/threshold/start date to appear
- week-based views should support a user setting for week start day
  - default can follow device locale/system behavior
  - user should be able to choose Sunday or Monday explicitly
- `upcoming` should be a user-configurable future window
  - default: next 14 days
  - configurable in settings
  - tasks must have a `due:` date or `t:`/threshold/start date to appear
- `overdue` should have separate sections for:
  - tasks with overdue due dates
  - tasks with overdue threshold/start dates
- date lenses should appear in both places:
  - quick-access chips/tabs on the main screen
  - built-in entries in the filter drawer
  - main-screen quick access should also include an obvious `all` chip/tab to return to the normal unscoped list
- activating a date lens should replace the current filter state rather than stack on top of existing filters
- date lenses should respect normal display and presentation settings such as:
  - hide completed
  - hide hidden tasks
  - hide lists/tags
  - sort order
- agenda mode should start as a composition of built-in date lenses
  - v1 sections should be fixed and ordered as: `overdue`, `today`, `this_week`, `upcoming`
  - empty sections should be hidden in v1
  - if a task qualifies through both `due:` and `t:` logic, it should appear only once in the highest-priority section
  - `due:` should outrank `t:`/threshold when resolving section placement
  - if a task is overdue by `due:` and also matches `t:`/threshold for today, it should still appear under `overdue`
  - even if a threshold/start date is after the due date for some reason, overdue due-date status should still win
  - it can evolve later into a more separate planner view if real use cases justify it
- agenda-mode direction should stay simple at first
  - use org-mode agenda as inspiration, not as a complexity target
  - prioritize features that solve real problems first
  - maintain a backlog for future ideas
  - use real user feedback to guide later expansion
  - section customization and reordering can wait until v2 or later
