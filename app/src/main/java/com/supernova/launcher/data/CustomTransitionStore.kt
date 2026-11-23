package com.supernova.launcher.data

import android.content.Context

// Where a Labs custom transition lives: one JSON document in its own small
// preferences file. It is deliberately outside the main store for now - a
// Labs experiment should be clearable without touching a backup or the
// settings schema. Sharing is the JSON text itself.
object CustomTransitionStore {

    private const val PREFS = "custom_transition"
    private const val KEY = "spec"

    fun raw(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)

    fun load(context: Context): CustomTransition? = raw(context)?.let(CustomTransition::parse)

    // True when it parsed and was saved; false leaves the old one in place.
    fun save(context: Context, text: String): Boolean {
        val transition = CustomTransition.parse(text) ?: return false
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, CustomTransition.encode(transition)).apply()
        return true
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
