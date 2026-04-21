/**
 * This file is part of Simpletask.

 * Copyright (c) 2009-2012 Todo.txt contributors (http://todotxt.com)
 * Copyright (c) 2013- Mark Janssen

 * LICENSE:

 * Todo.txt Touch is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any
 * later version.

 * Todo.txt Touch is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.

 * You should have received a copy of the GNU General Public License along with Todo.txt Touch.  If not, see
 * //www.gnu.org/licenses/>.

 * @author Mark Janssen
 * *
 * @license http://www.gnu.org/licenses/gpl.html
 * *
 * @copyright 2009-2012 Todo.txt contributors (http://todotxt.com)
 * *
 * @copyright 2013- Mark Janssen
 */
package nl.mpcjanssen.simpletask

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import nl.mpcjanssen.simpletask.fileswitch.FavoriteQuickAddShortcutModel

class AddTaskShortcut : ThemedNoActionBarActivity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")
        super.onCreate(savedInstanceState)
        showFavoriteFilePicker()
    }

    private fun showFavoriteFilePicker() {
        val favorites = TodoApplication.config.favoriteTodoFiles
        if (favorites.isEmpty()) {
            setupShortcut(null, getString(R.string.shortcut_addtask_name))
            finish()
            return
        }

        val specs = FavoriteQuickAddShortcutModel.buildSpecs(favorites)
        val labels = mutableListOf<String>().apply {
            add(getString(R.string.shortcut_addtask_name))
            addAll(specs.map { getString(R.string.shortcut_addtask_name_for_file, it.label) })
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.shortcut_addtask_choose_file)
            .setItems(labels) { _, which ->
                if (which == 0) {
                    setupShortcut(null, labels[which])
                } else {
                    setupShortcut(specs[which - 1].favorite.path, labels[which])
                }
                finish()
            }
            .setOnCancelListener {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .show()
    }

    private fun setupShortcut(targetPath: String?, shortcutName: String) {
        val shortcutIntent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(this@AddTaskShortcut, AddTask::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(Constants.EXTRA_FROM_LAUNCHER_SHORTCUT, true)
            targetPath?.let { putExtra(Constants.EXTRA_TARGET_TODO_FILE, it) }
        }

        val intent = Intent()
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName)
        val iconResource = Intent.ShortcutIconResource.fromContext(this,
                R.drawable.ic_launcher)
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource)

        setResult(Activity.RESULT_OK, intent)
    }

    companion object {
        private val TAG = AddTaskShortcut::class.java.simpleName
    }
}
