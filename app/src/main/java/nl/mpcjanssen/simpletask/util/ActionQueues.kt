package nl.mpcjanssen.simpletask.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.concurrent.thread


open class ActionQueue(val qName: String) : Thread() {


    fun add(description: String, r: () -> Unit) {
        Log.i(qName, "-> $description")
        thread(start = true, name = qName) {
            Log.i(qName, "<- $description")
            r.invoke()
        }
    }
}

object FileStoreActionQueue : ActionQueue("FSQ")


