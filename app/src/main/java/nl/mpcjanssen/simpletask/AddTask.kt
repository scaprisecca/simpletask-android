/**
 * This file is part of Simpletask.
 *
 * @copyright 2013- Mark Janssen
 */
package nl.mpcjanssen.simpletask

import android.app.DatePickerDialog
import android.content.*
import android.os.Bundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.appcompat.app.AlertDialog
import android.text.InputType
import android.text.Selection
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.Window
import android.view.WindowManager
import hirondelle.date4j.DateTime
import nl.mpcjanssen.simpletask.databinding.AddTaskBinding
import nl.mpcjanssen.simpletask.notifications.findPinnedTask
import nl.mpcjanssen.simpletask.notifications.replacePinnedTaskInFileLines
import nl.mpcjanssen.simpletask.remote.FileStore
import nl.mpcjanssen.simpletask.task.Priority
import nl.mpcjanssen.simpletask.task.Task
import nl.mpcjanssen.simpletask.util.*
import java.io.IOException
import java.util.*

class AddTask : ThemedActionBarActivity() {
    private var startText: String = ""
    private val selectionSnapshotTracker = SelectionSnapshotTracker()

    private val shareText: String? = null

    // private val m_backup = ArrayList<Task>()

    private var mBroadcastReceiver: BroadcastReceiver? = null
    private var localBroadcastManager: LocalBroadcastManager? = null
    private lateinit var binding: AddTaskBinding
    private lateinit var quickAddTargetResolution: QuickAddTargetResolution
    private var launchedFromShortcut = false
    private var usesIsolatedTargetMetadata = false
    private var targetMetadataSuggestions = QuickAddMetadataSuggestions(emptyList(), emptyList())
    private var explicitTargetEditOriginalText: String? = null
    private var explicitTargetEditPinnedTaskKey: String? = null
    /*
        Deprecated functions still work fine.
        For now keep using the old version, will updated if it breaks.
     */
    @Suppress("DEPRECATION")
    public override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
        super.onCreate(savedInstanceState)

        TodoApplication.app.loadTodoList("before adding tasks")

        val intentFilter = IntentFilter()
        intentFilter.addAction(Constants.BROADCAST_SYNC_START)
        intentFilter.addAction(Constants.BROADCAST_SYNC_DONE)

        localBroadcastManager = TodoApplication.app.localBroadCastManager

        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Constants.BROADCAST_SYNC_START) {
                    setProgressBarIndeterminateVisibility(true)
                } else if (intent.action == Constants.BROADCAST_SYNC_DONE) {
                    setProgressBarIndeterminateVisibility(false)
                }
            }
        }
        localBroadcastManager!!.registerReceiver(broadcastReceiver, intentFilter)
        mBroadcastReceiver = broadcastReceiver
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        binding = AddTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp)
        if (!TodoApplication.config.useListAndTagIcons) {
            binding.btnContext.setImageResource(R.drawable.ic_action_todotxt_lists)
            binding.btnProject.setImageResource(R.drawable.ic_action_todotxt_tags)

        }


        if (shareText != null) {
            binding.taskText.setText(shareText)
        }

        setTitle(R.string.addtask)
        launchedFromShortcut = intent.getBooleanExtra(Constants.EXTRA_FROM_LAUNCHER_SHORTCUT, false)
        quickAddTargetResolution = QuickAddTarget.resolve(intent.getStringExtra(Constants.EXTRA_TARGET_TODO_FILE), TodoApplication.config.todoFile)
        if (QuickAddSession.shouldBlockLaunch(quickAddTargetResolution.hasExplicitTarget, activeEditorCount)) {
            showToastLong(this, R.string.shortcut_addtask_finish_current_edit)
            closeShortcutCapture()
            return
        }
        usesIsolatedTargetMetadata = QuickAddSession.usesIsolatedMetadata(
            quickAddTargetResolution,
            TodoApplication.config.todoFile
        ) && QuickAddTarget.isExplicitTargetAllowed(
            quickAddTargetResolution.targetFile,
            TodoApplication.config.favoriteTodoFiles
        )
        if (usesIsolatedTargetMetadata) {
            loadTargetMetadataSuggestions(quickAddTargetResolution.targetFile)
        }

        Log.d(TAG, "Fill addtask")

        val taskId = intent.getStringExtra(Constants.EXTRA_TASK_ID)
        val pinnedTaskKey = intent.getStringExtra(Constants.EXTRA_PINNED_TASK_KEY)
        val pinnedTaskText = intent.getStringExtra(Constants.EXTRA_PINNED_TASK_TEXT)
        val directPinnedTargetEdit = pinnedTaskKey != null && quickAddTargetResolution.hasExplicitTarget
        val taskToEdit = when {
            taskId != null -> TodoApplication.todoList.getTaskWithId(taskId)
            pinnedTaskKey != null && pinnedTaskText != null && !directPinnedTargetEdit ->
                findPinnedTask(pinnedTaskKey, pinnedTaskText, TodoApplication.todoList.allTasks())
            else -> null
        }
        if (taskToEdit != null) {
            TodoApplication.todoList.pendingEdits.add(taskToEdit)
        }
        val explicitTargetEditPrefill = if (pinnedTaskKey != null && pinnedTaskText != null && directPinnedTargetEdit) {
            loadPinnedTaskTextFromExplicitTarget(pinnedTaskKey, pinnedTaskText, quickAddTargetResolution.targetFile)
        } else {
            null
        }
        explicitTargetEditOriginalText = explicitTargetEditPrefill
        explicitTargetEditPinnedTaskKey = if (explicitTargetEditPrefill != null) pinnedTaskKey else null

        val pendingTasks = TodoApplication.todoList.pendingEdits.map { it.inFileFormat(TodoApplication.config.useUUIDs) }
            val preFillString: String = when {
                pendingTasks.isNotEmpty() -> {
                    setTitle(R.string.updatetask)
                    join(pendingTasks, "\n")
                }
                explicitTargetEditPrefill != null -> {
                    setTitle(R.string.updatetask)
                    explicitTargetEditPrefill
                }
                intent.hasExtra(Constants.EXTRA_PREFILL_TEXT) -> intent.getStringExtra(Constants.EXTRA_PREFILL_TEXT) ?: ""
                intent.hasExtra(Query.INTENT_JSON) -> Query(intent, luaModule = "from_intent").prefill
                else -> ""
            }
            startText = preFillString
            // Avoid discarding changes on rotate
            if (binding.taskText.text.isEmpty()) {
                binding.taskText.setText(preFillString)
            }

            setInputType()
            binding.taskText.onSelectionChangedListener = { start, end ->
                selectionSnapshotTracker.remember(
                        AddTaskSelection.snapshot(start, end, binding.taskText.text.length),
                        fromFocusedEditor = true
                )
            }

            // Set button callbacks
            binding.btnContext.setOnClickListener { showListMenu() }
            binding.btnProject.setOnClickListener { showTagMenu() }
            binding.btnPrio.setOnClickListener { showPriorityMenu() }
            binding.btnDue.setOnClickListener { insertDate(DateType.DUE) }
            binding.btnThreshold.setOnClickListener { insertDate(DateType.THRESHOLD) }
            binding.btnNext.setOnClickListener { addPrefilledTask() }
            binding.btnSave.setOnClickListener { saveTasksAndClose() }
            binding.taskText.requestFocus()
            Selection.setSelection(binding.taskText.text,0)
            rememberCurrentSelection()

    }

    private fun addPrefilledTask() {
        val position = binding.taskText.selectionStart
        val remainingText = binding.taskText.text.toString().substring(position)
        val endOfLineDistance = remainingText.indexOf('\n')
        var endOfLine: Int
        endOfLine = if (endOfLineDistance == -1) {
            binding.taskText.length()
        } else {
            position + endOfLineDistance
        }
        binding.taskText.setSelection(endOfLine)
        replaceTextAtSelection("\n", false)

        val precedingText = binding.taskText.text.toString().substring(0, endOfLine)
        val lineStart = precedingText.lastIndexOf('\n')
        val line: String
        line = if (lineStart != -1) {
            precedingText.substring(lineStart, endOfLine)
        } else {
            precedingText
        }
        val t = Task(line)
        val prefillItems = mutableListOf<String>()
        t.lists?.let {lists ->
            prefillItems.addAll(lists.map { "@$it" })
        }
        t.tags?.let {tags ->
            prefillItems.addAll(tags.map { "+$it" })
        }

        replaceTextAtSelection(join(prefillItems, " "), true)

        endOfLine++
        binding.taskText.setSelection(endOfLine)
    }

    private fun setWordWrap(bool: Boolean) {
        binding.taskText.setHorizontallyScrolling(!bool)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        val inflater = menuInflater
        inflater.inflate(R.menu.add_task, menu)

        // Set checkboxes
        val menuWordWrap = menu.findItem(R.id.menu_word_wrap)
        menuWordWrap.isChecked = TodoApplication.config.isWordWrap

        val menuCapitalizeTasks = menu.findItem(R.id.menu_capitalize_tasks)
        menuCapitalizeTasks.isChecked = TodoApplication.config.isCapitalizeTasks

        return true
    }

    private fun setInputType() {
        val basicType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        if (TodoApplication.config.isCapitalizeTasks) {
            binding.taskText.inputType = basicType or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        } else {
            binding.taskText.inputType = basicType
        }
        setWordWrap(TodoApplication.config.isWordWrap)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
        // Respond to the action bar's Up/Home button
            android.R.id.home -> {
                finishEdit(confirmation = true)
            }
            R.id.menu_word_wrap -> {
                val newVal = !TodoApplication.config.isWordWrap
                TodoApplication.config.isWordWrap = newVal
                setWordWrap(newVal)
                item.isChecked = !item.isChecked
            }
            R.id.menu_capitalize_tasks -> {
                TodoApplication.config.isCapitalizeTasks = !TodoApplication.config.isCapitalizeTasks
                setInputType()
                item.isChecked = !item.isChecked
            }
            R.id.menu_help -> {
                showHelp()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showHelp() {
        val i = Intent(this, HelpScreen::class.java)
        i.putExtra(Constants.EXTRA_HELP_PAGE, getText(R.string.help_add_task))
        startActivity(i)
    }

    private fun saveTasksAndClose() {
        val todoList = TodoApplication.todoList
        // strip line breaks
        val input: String = binding.taskText.text.toString()

        // Don't add empty tasks
        if (input.trim { it <= ' ' }.isEmpty()) {
            Log.i(TAG, "Not adding empty line")
            closeShortcutCapture()
            return
        }

        // Update the TodoList with changes
        val enteredTasks = getTasks().dropLastWhile { it.text.isEmpty() }.map { task ->
            if (TodoApplication.config.hasPrependDate) {
                Task(task.text, todayAsString)
            } else {
                task
            }
        }
        val origTasks = todoList.pendingEdits
        val targetResolution = quickAddTargetResolution
        val pinnedTargetEditOriginalText = explicitTargetEditOriginalText
        val pinnedTargetEditTaskKey = explicitTargetEditPinnedTaskKey
        Log.i(TAG, "Saving ${enteredTasks.size} tasks, updating $origTasks tasks")

        if (pinnedTargetEditTaskKey != null && pinnedTargetEditOriginalText != null) {
            if (enteredTasks.size != 1) {
                showToastShort(this, R.string.shortcut_addtask_new_tasks_only)
                return
            }
            savePinnedTaskEditToExplicitTarget(
                taskKey = pinnedTargetEditTaskKey,
                targetFile = targetResolution.targetFile,
                updatedTask = enteredTasks.single(),
                onSuccess = { finishEdit(confirmation = false) }
            )
            return
        }

        if (targetResolution.hasExplicitTarget && origTasks.isNotEmpty()) {
            showToastShort(this, R.string.shortcut_addtask_new_tasks_only)
            return
        }

        if (targetResolution.hasExplicitTarget && targetResolution.targetFile != TodoApplication.config.todoFile) {
            if (!QuickAddTarget.isExplicitTargetAllowed(targetResolution.targetFile, TodoApplication.config.favoriteTodoFiles) || !FileStore.isOnline) {
                showToastLong(this, getString(R.string.favorite_file_unavailable, targetResolution.targetFile.name))
                return
            }
            saveTasksToExplicitTarget(
                targetResolution.targetFile,
                enteredTasks,
                onSuccess = { finishEdit(confirmation = false) }
            )
            return
        }

        if (origTasks.size == 1 && enteredTasks.size == 1) {
            TodoApplication.pinnedTaskNotifications.retargetPinnedTaskForTaskEdit(
                task = origTasks.first(),
                updatedTaskText = enteredTasks.single().text
            )
        }
        todoList.update(origTasks, enteredTasks, TodoApplication.config.hasAppendAtEnd)

        // Save
        todoList.notifyTasklistChanged(TodoApplication.config.todoFile, save = true, refreshMainUI = false)
        finishEdit(confirmation = false)
    }

    private fun saveTasksToExplicitTarget(
        targetFile: java.io.File,
        enteredTasks: List<Task>,
        onSuccess: () -> Unit
    ) {
        val newLines = enteredTasks.map { it.inFileFormat(TodoApplication.config.useUUIDs) }
        FileStoreActionQueue.add("Save shortcut quick-add task") {
            try {
                synchronized(explicitTargetSaveLock) {
                    val existingLines = FileStore.loadTasksFromFile(targetFile)
                    val mergedLines = QuickAddTarget.mergeExistingAndNewLines(
                            existingLines = existingLines,
                            newLines = newLines,
                            appendAtEnd = TodoApplication.config.hasAppendAtEnd
                    )
                    val savedFile = FileStore.saveTasksToFile(targetFile, mergedLines, TodoApplication.config.eol)
                    val persistedLines = FileStore.loadTasksFromFile(savedFile)
                    if (persistedLines != mergedLines) {
                        throw IOException("Quick-add save verification failed for ${targetFile.path}")
                    }
                }
                runOnMainThread(Runnable {
                    showToastShort(TodoApplication.app, R.string.task_added)
                    broadcastRefreshWidgets(TodoApplication.app.localBroadCastManager)
                    onSuccess()
                })
            } catch (e: Exception) {
                Log.e(TAG, "Quick-add save to ${targetFile.path} failed", e)
                runOnMainThread(Runnable {
                    showToastLong(TodoApplication.app, getString(R.string.favorite_file_unavailable, targetFile.name))
                })
            }
        }
    }

    private fun loadPinnedTaskTextFromExplicitTarget(taskKey: String, taskText: String, targetFile: java.io.File): String? {
        return try {
            findPinnedTask(taskKey, taskText, FileStore.loadTasksFromFile(targetFile).map(::Task))?.text
        } catch (e: Exception) {
            Log.e(TAG, "Unable to load pinned task $taskKey from ${targetFile.path}", e)
            null
        }
    }

    private fun savePinnedTaskEditToExplicitTarget(
        taskKey: String,
        targetFile: java.io.File,
        updatedTask: Task,
        onSuccess: () -> Unit
    ) {
        FileStoreActionQueue.add("Save pinned task edit") {
            try {
                synchronized(explicitTargetSaveLock) {
                    val record = TodoApplication.db.pinnedTaskRecordDao().get(taskKey)
                        ?: throw IOException("Pinned task record missing for $taskKey")
                    val existingLines = FileStore.loadTasksFromFile(targetFile)
                    val updatedLine = updatedTask.inFileFormat(TodoApplication.config.useUUIDs)
                    val updatedLines = replacePinnedTaskInFileLines(record, existingLines, updatedLine)
                        ?: throw IOException("Pinned task line not found in ${targetFile.path}")
                    val savedFile = FileStore.saveTasksToFile(targetFile, updatedLines, TodoApplication.config.eol)
                    val persistedLines = FileStore.loadTasksFromFile(savedFile)
                    if (persistedLines != updatedLines) {
                        throw IOException("Pinned task edit verification failed for ${targetFile.path}")
                    }
                    TodoApplication.pinnedTaskNotifications.retargetPinnedTaskForTaskEdit(
                        taskKey = taskKey,
                        updatedTaskText = Task(updatedLine).text
                    )
                }
                runOnMainThread(Runnable {
                    broadcastRefreshWidgets(TodoApplication.app.localBroadCastManager)
                    if (targetFile == TodoApplication.config.todoFile) {
                        TodoApplication.app.loadTodoList("Pinned notification edit")
                    }
                    onSuccess()
                })
            } catch (e: Exception) {
                Log.e(TAG, "Pinned task edit save to ${targetFile.path} failed", e)
                runOnMainThread(Runnable {
                    showToastLong(TodoApplication.app, getString(R.string.favorite_file_unavailable, targetFile.name))
                })
            }
        }
    }

    private fun closeShortcutCapture() {
        if (launchedFromShortcut && TodoApplication.atLeastAPI(21)) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }

    private fun finishEdit(confirmation: Boolean) {
        val close = DialogInterface.OnClickListener { _, _ ->
            TodoApplication.todoList.clearPendingEdits()
            closeShortcutCapture()
        }
        if (confirmation && (binding.taskText.text.toString() != startText)) {
            showConfirmationDialog(this, R.string.cancel_changes, close, null)
        } else {
            close.onClick(null, 0)
        }

    }

    override fun onBackPressed() {
        saveTasksAndClose()
    }

    private fun insertDate(dateType: DateType) {
        var titleId = R.string.defer_due
        if (dateType === DateType.THRESHOLD) {
            titleId = R.string.defer_threshold
        }
        val d = createDeferDialog(this, titleId, object : InputDialogListener {
            /*
                Deprecated functions still work fine.
                For now keep using the old version, will updated if it breaks.
            */
            @Suppress("DEPRECATION")
            override fun onClick(input: String) {
                if (input == "pick") {
                    /* Note on some Android versions the OnDateSetListener can fire twice
                     * https://code.google.com/p/android/issues/detail?id=34860
                     * With the current implementation which replaces the dates this is not an
                     * issue. The date is just replaced twice
                     */
                    val today = DateTime.today(TimeZone.getDefault())
                    val dialog = DatePickerDialog(this@AddTask, DatePickerDialog.OnDateSetListener { _, year, month, day ->
                        val date = DateTime.forDateOnly(year, month + 1, day)
                        insertDateAtSelection(dateType, date)
                    },
                            today.year!!,
                            today.month!! - 1,
                            today.day!!)

                    val showCalendar = TodoApplication.config.showCalendar
                    dialog.datePicker.calendarViewShown = showCalendar
                    dialog.datePicker.spinnersShown = !showCalendar
                    dialog.show()
                } else {
                    if (!input.isEmpty()) {
                        insertDateAtSelection(dateType, addInterval(DateTime.today(TimeZone.getDefault()), input))
                    } else {
                        replaceDate(dateType, input)
                    }
                }
            }
        })
        d.show()
    }

    private fun replaceDate(dateType: DateType, date: String) {
        if (dateType === DateType.DUE) {
            replaceDueDate(date)
        } else {
            replaceThresholdDate(date)
        }
    }

    private fun insertDateAtSelection(dateType: DateType, date: DateTime?) {
        date?.let {
            replaceDate(dateType, date.format("YYYY-MM-DD"))
        }
    }

    private fun showTagMenu() {
        val items = TreeSet<String>()
        val snapshot = selectionSnapshotForMutation()

        items.addAll(projectSuggestions())
        // Also display projects in tasks being added
        val tasks = getTasks()
        if (tasks.size == 0) {
            tasks.add(Task(""))
        }
        tasks.forEach {task ->
            task.tags?.let {items.addAll(it)}
        }
        val idx = getCurrentCursorLine(snapshot)
        val task = getTasks().getOrElse(idx) { Task("") }

        updateItemsDialog(
                TodoApplication.config.tagTerm,
                listOf(task),
                ArrayList(items),
                Task::tags,
                Task::addTag,
                Task::removeTag
        ) {
            if (idx != -1) {
                tasks[idx] = task
            } else {
                tasks.add(task)
            }
            replaceTextAndRestoreSelection(tasks.joinToString("\n") { it.text }, snapshot, false, restoreToLineEnd = true)
        }
    }

    private fun showPriorityMenu() {
        val builder = AlertDialog.Builder(this)
        val snapshot = selectionSnapshotForMutation()
        val priorities = Priority.values()
        val priorityCodes = priorities.mapTo(ArrayList()) { it.code }

        builder.setItems(priorityCodes.toArray<String>(arrayOfNulls<String>(priorityCodes.size))
        ) { _, which -> replacePriority(priorities[which].code, snapshot) }

        // Create the AlertDialog
        val dialog = builder.create()
        dialog.setTitle(R.string.priority_prompt)
        dialog.show()
    }

    private fun getTasks(): MutableList<Task> {
        val input = binding.taskText.text.toString()
        return input.split("\r\n|\r|\n".toRegex()).asSequence().map(::Task).toMutableList()
    }

    private fun contextSuggestions(): List<String> {
        return if (usesIsolatedTargetMetadata) {
            targetMetadataSuggestions.contexts
        } else {
            TodoApplication.todoList.contexts
        }
    }

    private fun projectSuggestions(): List<String> {
        return if (usesIsolatedTargetMetadata) {
            targetMetadataSuggestions.projects
        } else {
            TodoApplication.todoList.projects
        }
    }

    private fun loadTargetMetadataSuggestions(targetFile: java.io.File) {
        FileStoreActionQueue.add("Load quick-add target metadata") {
            try {
                val suggestions = QuickAddMetadata.collectSuggestions(FileStore.loadTasksFromFile(targetFile))
                runOnMainThread(Runnable {
                    targetMetadataSuggestions = suggestions
                })
            } catch (e: Exception) {
                Log.w(TAG, "Unable to load quick-add metadata for ${targetFile.path}", e)
            }
        }
    }

    private fun showListMenu() {
        val items = TreeSet<String>()
        val snapshot = selectionSnapshotForMutation()

        items.addAll(contextSuggestions())
        // Also display contexts in tasks being added
        val tasks = getTasks()
        if (tasks.size == 0) {
            tasks.add(Task(""))
        }
        tasks.forEach {task ->
            task.lists?.let {items.addAll(it)}
        }

        val idx = getCurrentCursorLine(snapshot)
        val task = getTasks().getOrElse(idx) { Task("") }

        updateItemsDialog(
                TodoApplication.config.listTerm,
                listOf(task),
                ArrayList(items),
                Task::lists,
                Task::addList,
                Task::removeList
        ) {
            if (idx != -1) {
                tasks[idx] = task
            } else {
                tasks.add(task)
            }
            replaceTextAndRestoreSelection(tasks.joinToString("\n") { it.text }, snapshot, false, restoreToLineEnd = true)
        }
    }

    private fun getCurrentCursorLine(snapshot: SelectionSnapshot = selectionSnapshotForMutation()): Int {
        return AddTaskSelection.currentLine(binding.taskText.text, snapshot)
    }

    private fun replaceDueDate(newDueDate: CharSequence) {
        mutateCurrentTask(selectionSnapshotForMutation(), moveCursor = false, restoreToLineEnd = true) { task ->
            task.dueDate = newDueDate.toString()
        }
    }

    private fun replaceThresholdDate(newThresholdDate: CharSequence) {
        mutateCurrentTask(selectionSnapshotForMutation(), moveCursor = false, restoreToLineEnd = true) { task ->
            task.thresholdDate = newThresholdDate.toString()
        }
    }

    private fun replaceTextAndRestoreSelection(updatedText: String, snapshot: SelectionSnapshot, moveCursor: Boolean, restoreToLineEnd: Boolean = false) {
        val oldLength = binding.taskText.text.length
        binding.taskText.setText(updatedText)
        restoreSelection(snapshot, oldLength, moveCursor, restoreToLineEnd)
    }

    private fun restoreSelection(snapshot: SelectionSnapshot, oldLength: Int, moveCursor: Boolean, restoreToLineEnd: Boolean = false) {
        val newLocation = if (restoreToLineEnd) {
            AddTaskSelection.restoredCursorToLineEnd(binding.taskText.text, snapshot)
        } else {
            AddTaskSelection.restoredCursor(
                    selection = snapshot,
                    oldLength = oldLength,
                    newLength = binding.taskText.text.length,
                    moveCursor = moveCursor
            )
        }
        binding.taskText.setSelection(newLocation, newLocation)
        rememberCurrentSelection()
    }

    private fun mutateCurrentTask(snapshot: SelectionSnapshot, moveCursor: Boolean, restoreToLineEnd: Boolean = false, mutation: (Task) -> Unit) {
        val lines = ArrayList<String>()
        Collections.addAll(lines, *binding.taskText.text.toString().split("\\n".toRegex()).toTypedArray())

        val currentLine = AddTaskSelection.normalizeLineIndex(getCurrentCursorLine(snapshot), lines.size)
        if (currentLine != -1) {
            val task = Task(lines[currentLine])
            mutation(task)
            lines[currentLine] = task.inFileFormat(TodoApplication.config.useUUIDs)
            replaceTextAndRestoreSelection(join(lines, "\n"), snapshot, moveCursor, restoreToLineEnd)
        }
    }

    private fun replacePriority(newPriority: CharSequence, snapshot: SelectionSnapshot = selectionSnapshotForMutation()) {
        mutateCurrentTask(snapshot, moveCursor = true) { task ->
            Log.d(TAG, "Changing priority from " + task.priority.toString() + " to " + newPriority.toString())
            task.priority = Priority.toPriority(newPriority.toString())
        }
    }

    private fun selectionSnapshotForMutation(): SelectionSnapshot {
        val trackedSelection = selectionSnapshotTracker.current()
        if (trackedSelection.isValid) {
            return trackedSelection
        }

        val liveSelection = captureSelectionSnapshot()
        selectionSnapshotTracker.remember(liveSelection, fromFocusedEditor = binding.taskText.hasFocus())
        return selectionSnapshotTracker.current()
    }

    private fun captureSelectionSnapshot(): SelectionSnapshot {
        return AddTaskSelection.snapshot(
                binding.taskText.selectionStart,
                binding.taskText.selectionEnd,
                binding.taskText.text.length
        )
    }

    private fun rememberCurrentSelection() {
        selectionSnapshotTracker.remember(captureSelectionSnapshot(), fromFocusedEditor = binding.taskText.hasFocus())
    }

    private fun replaceTextAtSelection(newText: CharSequence, spaces: Boolean) {
        var text = newText
        val start = binding.taskText.selectionStart
        val end = binding.taskText.selectionEnd
        if (start == end && start != 0 && spaces) {
            // no selection prefix with space if needed
            if (binding.taskText.text[start - 1] != ' ') {
                text = " $text"
            }
        }
        binding.taskText.text.replace(Math.min(start, end), Math.max(start, end),
                text, 0, text.length)
    }

    public override fun onStart() {
        super.onStart()
        activeEditorCount += 1
    }

    public override fun onStop() {
        activeEditorCount = maxOf(0, activeEditorCount - 1)
        super.onStop()
    }

    public override fun onDestroy() {
        super.onDestroy()
        mBroadcastReceiver?.let {
            localBroadcastManager?.unregisterReceiver(it)
        }
    }

    companion object {
        private const val TAG = "AddTask"
        private val explicitTargetSaveLock = Any()
        private var activeEditorCount = 0
    }
}
