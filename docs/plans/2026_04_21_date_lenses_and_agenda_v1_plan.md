# date lenses and agenda v1 plan

created: 2026-04-21
status: active
source_doc: docs/ideation/2026_04_21_simpletasks_workflow_ideation.md

## objective
- build first-class date lenses for the main task list: `all`, `overdue`, `today`, `this_week`, and `upcoming`
- make date lenses accessible from both main-screen quick chips/tabs and the quick-filter drawer
- add the settings needed to make week-based behavior and upcoming-window behavior user-friendly
- define the task-classification architecture so agenda v1 can be built on top of the same rules instead of re-implementing them later
- prepare an implementation artifact that Codex can execute without rediscovering product rules or repo patterns

## scope
### included
- explicit date-lens domain model and classification rules
- persistence of the active date lens in the main query state
- `all` chip/tab to restore the normal unscoped list
- main-screen quick-access chip row for date lenses
- quick-filter drawer support for date-lens entries
- settings for:
  - week start override
  - upcoming window days
- filter-title updates so active date lenses are visible in the existing filter bar
- pure JVM tests for date-lens classification and precedence behavior
- implementation groundwork for agenda v1 through shared classification structures and section definitions

### excluded
- full agenda v1 screen implementation in this first build handoff
- user-configurable agenda section order
- custom agenda sections beyond `overdue`, `today`, `this_week`, and `upcoming`
- widget-specific date-lens UI in this pass
- Lua recipe gallery or new Lua UX in this pass
- changing calendar mode into agenda mode
- stacking date lenses on top of arbitrary existing structural filters

## confirmed_product_rules
- top-level date lenses: `all`, `overdue`, `today`, `this_week`, `upcoming`
- date lenses replace the current lens state instead of stacking with another date lens
- normal display/presentation settings still apply:
  - hide completed
  - hide hidden tasks
  - hide lists/tags
  - sort order
- `today` includes:
  - overdue tasks
  - tasks due today
  - tasks whose threshold/start date is today
  - tasks with threshold/start today even if their due date is later
- `this_week`:
  - excludes overdue tasks
  - includes tasks due this week
  - includes tasks whose threshold/start date is this week
  - only includes tasks with `due:` or `t:`/threshold dates
- `upcoming`:
  - is a user-configurable future window
  - defaults to 14 days
  - only includes tasks with `due:` or `t:`/threshold dates
- `overdue` should distinguish:
  - overdue due dates
  - overdue threshold/start dates
- precedence rules:
  - tasks should appear once in the highest-priority classification
  - `due:` outranks `t:`/threshold
  - overdue `due:` status outranks threshold matches for today or later
  - if a threshold/start date is after the due date for some reason, overdue due-date status still wins
- week-based views should default to locale/system behavior but allow explicit Sunday/Monday override
- agenda v1 should later use fixed sections in this order:
  1. `overdue`
  2. `today`
  3. `this_week`
  4. `upcoming`
- agenda v1 should hide empty sections and defer customization to v2+

## architecture_decision
Use an explicit date-lens model in app code, not ad-hoc generated search filters and not Lua scripts.

Why:
- the product rules now include precedence, week-boundary logic, upcoming windows, and future agenda reuse
- those rules are too rich to hide in temporary generated query fields or opaque Lua snippets
- explicit types make it easier to:
  - persist the active lens in `Query`
  - render active chip state
  - expose built-in drawer entries
  - surface filter titles cleanly
  - reuse the exact same classifier for agenda v1 later

Recommended shape:
- a small `DateLens` enum or sealed model for `ALL`, `OVERDUE`, `TODAY`, `THIS_WEEK`, `UPCOMING`
- a pure classifier utility that accepts:
  - task
  - today date
  - week-start config
  - upcoming-window config
- classifier output rich enough for later agenda use, for example:
  - primary lens match
  - overdue subtype
  - due-vs-threshold reason
  - optional future agenda section

## relevant_existing_patterns
- `app/src/main/java/nl/mpcjanssen/simpletask/Query.kt`
  - current source of truth for filter state, JSON/prefs/intent serialization, `hasFilter()`, and filter title behavior
- `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
  - main UI controller, quick-filter drawer handling, filter bar updates, event wiring, and screen-mode transitions
- `app/src/main/res/layout/main.xml`
  - main screen layout where a chip row can be added without inventing a new screen
- `app/src/main/java/nl/mpcjanssen/simpletask/adapters/DrawerAdapter.kt`
  - current quick-filter drawer adapter for contexts/projects; likely needs replacement or expansion to support built-in lens items and mixed sections
- `app/src/main/java/nl/mpcjanssen/simpletask/util/Config.kt`
  - existing settings storage pattern for booleans/strings/ints and preference-backed app state
- `app/src/main/res/xml/interface_preferences.xml`
  - current home for interaction settings; likely best place for week-start and upcoming-window configuration
- `app/src/main/res/values/donottranslate.xml`
  - preference keys and stable internal string identifiers
- `app/src/main/java/nl/mpcjanssen/simpletask/task/Task.kt`
  - task date fields and date-related helpers
- `app/src/main/java/nl/mpcjanssen/simpletask/calendar/CalendarTaskProjector.kt`
  - existing pure task-to-date projection logic; useful precedent for building a pure date-lens classifier
- `app/src/test/java/nl/mpcjanssen/simpletask/calendar/CalendarTaskProjectorTest.kt`
  - example of small deterministic JVM tests around date projection logic
- `app/src/test/java/nl/mpcjanssen/simpletask/calendar/CalendarModeStateTest.kt`
  - precedent for state-oriented JVM tests

## implementation_units
### 1. introduce explicit date-lens domain model and classifier
- files:
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/dates/DateLens.kt`
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/dates/DateLensClassifier.kt`
  - create: `app/src/test/java/nl/mpcjanssen/simpletask/dates/DateLensClassifierTest.kt`
- changes:
  - define the built-in lenses and their stable storage values
  - define precedence rules in one place
  - implement pure classification for:
    - overdue due
    - overdue threshold
    - today
    - this week
    - upcoming
  - expose helper output that later agenda mode can reuse for sectioning
- dependencies:
  - `Task.kt` date fields
  - date utilities already used in task/calendar code
- risks:
  - week-boundary logic can get subtly wrong around locale vs override behavior
  - duplication bugs if classifier does not return one clear primary placement
- tests:
  - `app/src/test/java/nl/mpcjanssen/simpletask/dates/DateLensClassifierTest.kt`
- acceptance:
  - every product rule above is covered by deterministic JVM tests
  - classifier has a single source of truth for precedence
  - agenda reuse is obvious from the API shape

### 2. persist active date lens in query state
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Query.kt`
  - create: `app/src/test/java/nl/mpcjanssen/simpletask/DateLensQueryStateTest.kt`
- changes:
  - add a persisted `dateLens` field to `Query`
  - serialize/deserialize it through JSON, prefs, and intents
  - make `clear()` restore `ALL` or no-lens state
  - update `hasFilter()` and `getTitle(...)` so an active lens is visible in the filter bar text
  - keep saved filter and widget compatibility in mind; missing field must round-trip safely
- dependencies:
  - new `DateLens` model
- risks:
  - breaking existing saved query deserialization if the new field is not optional by default
  - incorrect `hasFilter()` behavior causing the bar or back-clear logic to misbehave
- tests:
  - `app/src/test/java/nl/mpcjanssen/simpletask/DateLensQueryStateTest.kt`
- acceptance:
  - old saved filters still load without crashes
  - active lens survives app restarts and intent launches
  - filter title shows the active lens name

### 3. apply date-lens filtering in the main list pipeline
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Query.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/task/TodoList.kt`
  - create: `app/src/test/java/nl/mpcjanssen/simpletask/DateLensQueryApplicationTest.kt`
- changes:
  - integrate the date-lens classifier into the existing filter path
  - keep structural display behavior intact:
    - hide completed
    - hide hidden
    - sort order
  - make date-lens filtering replace lens state, not piggyback on context/project drawer state
  - ensure `ALL` behaves like the normal unscoped list
- dependencies:
  - query persistence from unit 2
  - classifier from unit 1
- risks:
  - order of operations: date-lens classification vs hide-future/hide-completed interactions may become inconsistent if not explicitly defined
  - performance regressions if date logic is recomputed wastefully for large lists
- tests:
  - `app/src/test/java/nl/mpcjanssen/simpletask/DateLensQueryApplicationTest.kt`
- acceptance:
  - activating a lens produces the expected task set
  - `ALL` returns the normal list behavior
  - existing filters continue to function when no lens is active

### 4. add date-lens settings to config and preferences
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/util/Config.kt`
  - modify: `app/src/main/res/xml/interface_preferences.xml`
  - modify: `app/src/main/res/values/donottranslate.xml`
  - modify: `app/src/main/res/values/strings.xml`
  - create: `app/src/test/java/nl/mpcjanssen/simpletask/dates/DateLensSettingsTest.kt`
- changes:
  - add preference keys and config accessors for:
    - week start mode: locale/default, Sunday, Monday
    - upcoming window days: default 14, bounded integer setting
  - keep defaults safe and migration-friendly
  - expose config access in a way the classifier can consume without UI dependencies
- dependencies:
  - preference patterns already used in `Config.kt`
- risks:
  - choosing the wrong preference screen and making the feature hard to discover
  - weak validation for upcoming days leading to odd ranges
- tests:
  - `app/src/test/java/nl/mpcjanssen/simpletask/dates/DateLensSettingsTest.kt`
- acceptance:
  - defaults match product decisions
  - override behavior is easy to wire into classifier tests

### 5. add main-screen quick chips/tabs
- files:
  - modify: `app/src/main/res/layout/main.xml`
  - create: `app/src/main/res/layout/date_lens_chip_row.xml` or inline a chip group in `main.xml`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
  - modify: `app/src/main/res/values/strings.xml`
- changes:
  - add a small quick-access chip row below the filter bar and above the task list
  - chips should include:
    - `All`
    - `Overdue`
    - `Today`
    - `This Week`
    - `Upcoming`
  - selecting a chip should update `mainQuery.dateLens`, persist it, and refresh the list
  - `All` should restore the normal unscoped list
  - active lens state should render clearly
  - hide or visually minimize the row in calendar mode if needed to avoid mode clutter
- dependencies:
  - unit 2 query state
  - unit 3 list application
- risks:
  - crowding the top of the screen on smaller devices
  - conflicting with the existing filter bar if active-state messaging is redundant
- tests:
  - manual QA primarily
  - optional pure helper test if chip model generation is extracted
- acceptance:
  - chip taps are immediate and obvious
  - active chip always matches actual list state
  - `All` is always available

### 6. extend the quick-filter drawer with built-in date-lens entries
- files:
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/adapters/DrawerAdapter.kt`
  - or replace with: `app/src/main/java/nl/mpcjanssen/simpletask/adapters/QuickFilterDrawerAdapter.kt`
  - modify: `app/src/main/java/nl/mpcjanssen/simpletask/Simpletask.kt`
  - create: `app/src/test/java/nl/mpcjanssen/simpletask/adapters/QuickFilterDrawerModelTest.kt`
- changes:
  - add a built-in date-lens section above or before contexts/projects
  - keep contexts/projects quick filtering working as-is where possible
  - choose one of two approaches:
    - minimal patch: extend the existing adapter to support a date-lens header and lens items
    - cleaner path: replace it with a sectioned adapter/model that supports mixed row types cleanly
  - selecting a built-in date-lens entry should replace current lens state and refresh the list
  - drawer check-state should reflect the active lens
- dependencies:
  - chip behavior from unit 5 should define the shared lens-activation path
- risks:
  - the current `DrawerAdapter` is optimized for only two homogeneous sections and may become awkward if overextended
  - mixing context/project toggles with mutually exclusive date lenses can create confusing state if not clearly separated visually
- tests:
  - `app/src/test/java/nl/mpcjanssen/simpletask/adapters/QuickFilterDrawerModelTest.kt`
- acceptance:
  - lens entries are discoverable
  - activating a lens from drawer yields the same result as tapping the chip
  - existing context/project quick filters still behave correctly

### 7. prepare agenda v1 groundwork without building the screen yet
- files:
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/dates/AgendaSection.kt`
  - create: `app/src/main/java/nl/mpcjanssen/simpletask/dates/AgendaSectionBuilder.kt`
  - create: `app/src/test/java/nl/mpcjanssen/simpletask/dates/AgendaSectionBuilderTest.kt`
- changes:
  - reuse the classifier to build a sectioned model with fixed v1 order:
    - `overdue`
    - `today`
    - `this_week`
    - `upcoming`
  - hide empty sections in the resulting model
  - do not wire a UI yet unless implementation scope remains small after date lenses are stable
- dependencies:
  - unit 1 classifier
- risks:
  - tempting Codex to build the full agenda screen in the same branch before date lenses are stable
- tests:
  - `app/src/test/java/nl/mpcjanssen/simpletask/dates/AgendaSectionBuilderTest.kt`
- acceptance:
  - groundwork exists for a follow-up agenda screen without redoing classification logic

## sequencing
1. implement the pure date-lens model and classifier first
2. persist active lens state in `Query`
3. apply lens filtering in the main list pipeline
4. add settings in `Config` and preferences
5. add main-screen chips using the now-stable lens state
6. extend the quick-filter drawer to expose the same built-in lenses
7. add agenda-section groundwork only after the date-lens behavior is verified

## recommended_execution_boundary_for_codex
Use one Codex execution pass for units 1 through 6 only.

Why:
- that is already a meaningful, shippable feature
- it keeps the handoff focused on date lenses rather than bundling a second view mode
- agenda groundwork in unit 7 is useful, but optional if the branch starts to sprawl

If implementation stays tight and clean, unit 7 can be included at the end. Otherwise keep it as a follow-up plan or a narrowly-scoped add-on commit.

## risks_and_unknowns
- risk: `Query.hasFilter()` semantics may become confusing when `ALL` is active but contexts/projects/search are empty
  - mitigation: treat `ALL` as no date lens for `hasFilter()` purposes, and test title/back-clear behavior explicitly
- risk: the current quick-filter drawer architecture is biased toward contexts/projects and may resist a clean date-lens section
  - mitigation: prefer a small section-model refactor over patching in more special cases
- risk: week-boundary behavior may be ambiguous on devices with locale defaults
  - mitigation: test locale/default and explicit Sunday/Monday overrides separately
- risk: interaction between date lenses and existing `hideFuture` behavior may confuse users
  - mitigation: define and test whether `hideFuture` applies inside each lens; recommended behavior is that it still applies because the user asked to respect normal display settings
- risk: filter bar text could become noisy if both lens label and other filter labels are shown together later
  - mitigation: keep v1 lens activation replacing current lens state, and format title strings carefully
- risk: screen crowding on small devices once chips are added above the list
  - mitigation: use a horizontally scrollable chip row or compact chip sizing instead of forcing all chips into one rigid row

## validation_strategy
### unit tests
- classifier tests for every precedence rule and date-window rule
- query serialization tests for date-lens persistence
- query application tests for list membership under each lens
- settings tests for week-start and upcoming-window defaults/overrides
- quick-filter drawer model tests if the drawer data structure is extracted
- agenda-section builder tests if unit 7 is implemented

### build verification
- run the narrowest relevant test targets first, then a full cloudless build
- suggested commands:
```bash
JAVA_HOME="$HOME/.local/jdks/temurin-11" PATH="$HOME/.local/jdks/temurin-11/bin:$PATH" \
./gradlew :app:testCloudlessDebugUnitTest --tests nl.mpcjanssen.simpletask.dates.DateLensClassifierTest

./scripts/build_cloudless.sh
```

### manual QA
- confirm chip activation updates the list immediately
- confirm `All` restores the normal list
- confirm drawer lens entries and chips stay in sync
- confirm active lens survives process restart
- confirm `Today`, `This Week`, `Upcoming`, and `Overdue` each match the agreed rules using a seeded task file with edge cases
- confirm `hide completed`, `hide hidden`, and sort order still behave as expected while a lens is active
- confirm back-clear behavior still works sensibly when a date lens is active

## handoff_notes
- do not implement this as saved filters or Lua-backed generated scripts; use explicit app code
- prefer pure helpers and JVM tests for the date logic before touching UI
- keep `ALL` as a real explicit lens in UI, but treat it like unscoped behavior in filtering semantics
- be conservative with drawer changes; if `DrawerAdapter` becomes ugly, replace it with a sectioned adapter instead of stacking conditionals
- preserve existing saved filters, widget filters, and quick-filter behavior unless the plan explicitly changes them
- if scope starts expanding, ship units 1 through 6 first and leave agenda groundwork for a second pass
- after implementation, update `CHANGELOG.md` in the same commit because this is user-facing

## next_step_options
1. approve this plan and hand it to Codex with `ce-handoff`
2. tighten one or two remaining product edge cases before handoff
3. split agenda groundwork into a separate follow-up plan before execution
