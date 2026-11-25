package com.supernova.launcher.data

import android.content.Context

// Where a Labs custom transition lives: one JSON document in its own small
// preferences file. It is deliberately outside the main store for now - a
// Labs experiment should be clearable without touching a backup or the
// settings schema. Sharing is the JSON text itself.
object CustomTransitionStore {

    private const val PREFS = "custom_transition"
    private const val KEY = "spec"
    private const val PRESETS_KEY = "presets"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun raw(context: Context): String? = prefs(context).getString(KEY, null)

    fun load(context: Context): CustomTransition? = raw(context)?.let(CustomTransition::parse)

    // True when it parsed and was saved; false leaves the old one in place.
    fun save(context: Context, text: String): Boolean {
        val transition = CustomTransition.parse(text) ?: return false
        prefs(context).edit().putString(KEY, CustomTransition.encode(transition)).apply()
        return true
    }

    fun presetsRaw(context: Context): String? = prefs(context).getString(PRESETS_KEY, null)

    fun presets(context: Context): List<CustomTransition> =
        presetsRaw(context)?.let(CustomTransition::parseList) ?: emptyList()

    // A preset is keyed by its name: saving over a name replaces it.
    fun savePreset(context: Context, transition: CustomTransition): Boolean {
        val named = transition.copy(name = transition.name.trim().ifBlank { "Custom" })
        if (named.validated() == null) return false
        val list = (presets(context).filterNot { it.name.equals(named.name, ignoreCase = true) } + named)
            .takeLast(CustomTransition.MAX_PRESETS)
        prefs(context).edit()
            .putString(PRESETS_KEY, CustomTransition.encodeList(list))
            .apply()
        return true
    }

    fun deletePreset(context: Context, name: String) {
        val list = presets(context).filterNot { it.name.equals(name, ignoreCase = true) }
        prefs(context).edit()
            .putString(PRESETS_KEY, CustomTransition.encodeList(list))
            .apply()
    }

    // A backup's presets, already encoded; an empty list clears them.
    fun restorePresets(context: Context, raw: String) {
        val list = CustomTransition.parseList(raw)
        if (list.isEmpty()) clearPresets(context)
        else prefs(context).edit().putString(PRESETS_KEY, CustomTransition.encodeList(list)).apply()
    }

    fun clearPresets(context: Context) {
        prefs(context).edit().remove(PRESETS_KEY).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}
