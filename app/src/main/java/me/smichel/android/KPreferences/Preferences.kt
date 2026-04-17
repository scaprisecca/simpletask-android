package me.smichel.android.KPreferences

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class Preferences(protected val context: Context) {
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val callbacks = linkedMapOf<String, MutableList<() -> Unit>>()

    init {
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            callbacks[key]?.forEach { it.invoke() }
        }
    }

    protected fun getString(resId: Int): String = context.getString(resId)

    fun registerCallbacks(keys: List<String>, callback: () -> Unit) {
        keys.forEach { key ->
            callbacks.getOrPut(key) { mutableListOf() }.add(callback)
        }
    }

    inner class BooleanPreference(private val resId: Int, private val defaultValue: Boolean) : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
            return prefs.getBoolean(getString(resId), defaultValue)
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            prefs.edit().putBoolean(getString(resId), value).apply()
        }
    }

    inner class IntPreference(private val resId: Int, private val defaultValue: Int) : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            return prefs.getInt(getString(resId), defaultValue)
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            prefs.edit().putInt(getString(resId), value).apply()
        }
    }

    inner class StringPreference(private val resId: Int, private val defaultValue: String) : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String {
            return prefs.getString(getString(resId), defaultValue) ?: defaultValue
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            prefs.edit().putString(getString(resId), value).apply()
        }
    }

    inner class StringOrNullPreference(private val resId: Int) : ReadWriteProperty<Any?, String?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String? {
            return prefs.getString(getString(resId), null)
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
            prefs.edit().putString(getString(resId), value).apply()
        }
    }
}
