package com.biologigor.snake.data

import android.content.Context

/** Настройки игры + рекорд. Хранятся в SharedPreferences. */
data class Settings(
    val best: Int = 0,
    val sound: Boolean = true,
    val vibration: Boolean = true,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("snake", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        best = sp.getInt(KEY_BEST, 0),
        sound = sp.getBoolean(KEY_SOUND, true),
        vibration = sp.getBoolean(KEY_VIBRO, true),
    )

    fun save(s: Settings) {
        sp.edit()
            .putInt(KEY_BEST, s.best)
            .putBoolean(KEY_SOUND, s.sound)
            .putBoolean(KEY_VIBRO, s.vibration)
            .apply()
    }

    private companion object {
        const val KEY_BEST = "best"
        const val KEY_SOUND = "sound"
        const val KEY_VIBRO = "vibro"
    }
}
