package com.aulalogger.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

object ThemePrefs {

    private const val PREFS = "theme_prefs"
    private const val KEY_MODE = "theme_mode"

    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode

    /** Inicializa o flow a partir do disco. Chamar em Application.onCreate. */
    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _mode.value = ThemeMode.fromId(sp.getString(KEY_MODE, null))
    }

    fun set(context: Context, mode: ThemeMode) {
        _mode.value = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.id).apply()
    }
}
