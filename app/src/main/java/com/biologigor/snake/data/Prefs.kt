package com.biologigor.snake.data

import android.content.Context
import com.biologigor.snake.game.Difficulty

/** Настройки игры + рекорд каждой сложности. Хранятся в SharedPreferences. */
data class Settings(
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val bestEasy: Int = 0,
    val bestMedium: Int = 0,
    val bestHard: Int = 0,
    val sound: Boolean = true,
    val vibration: Boolean = true,
) {
    fun best(d: Difficulty): Int = when (d) {
        Difficulty.EASY -> bestEasy
        Difficulty.MEDIUM -> bestMedium
        Difficulty.HARD -> bestHard
    }

    fun withBest(d: Difficulty, value: Int): Settings = when (d) {
        Difficulty.EASY -> copy(bestEasy = value)
        Difficulty.MEDIUM -> copy(bestMedium = value)
        Difficulty.HARD -> copy(bestHard = value)
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("snake", Context.MODE_PRIVATE)

    fun load(): Settings {
        migrateLegacyBest()
        val difficulty = sp.getString(KEY_DIFFICULTY, null)
            ?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
            ?: Difficulty.MEDIUM
        return Settings(
            difficulty = difficulty,
            bestEasy = sp.getInt(KEY_BEST_EASY, 0),
            bestMedium = sp.getInt(KEY_BEST_MEDIUM, 0),
            bestHard = sp.getInt(KEY_BEST_HARD, 0),
            sound = sp.getBoolean(KEY_SOUND, true),
            vibration = sp.getBoolean(KEY_VIBRO, true),
        )
    }

    fun save(s: Settings) {
        sp.edit()
            .putString(KEY_DIFFICULTY, s.difficulty.name)
            .putInt(KEY_BEST_EASY, s.bestEasy)
            .putInt(KEY_BEST_MEDIUM, s.bestMedium)
            .putInt(KEY_BEST_HARD, s.bestHard)
            .putBoolean(KEY_SOUND, s.sound)
            .putBoolean(KEY_VIBRO, s.vibration)
            .apply()
    }

    /** Рекорд из старых версий был общий; тот разгон соответствует нынешней «Сложно». */
    private fun migrateLegacyBest() {
        if (!sp.contains(KEY_BEST)) return
        val legacy = sp.getInt(KEY_BEST, 0)
        val editor = sp.edit()
        if (!sp.contains(KEY_BEST_HARD) && legacy > 0) editor.putInt(KEY_BEST_HARD, legacy)
        editor.remove(KEY_BEST).apply()
    }

    private companion object {
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_BEST = "best"
        const val KEY_BEST_EASY = "best_easy"
        const val KEY_BEST_MEDIUM = "best_medium"
        const val KEY_BEST_HARD = "best_hard"
        const val KEY_SOUND = "sound"
        const val KEY_VIBRO = "vibro"
    }
}
