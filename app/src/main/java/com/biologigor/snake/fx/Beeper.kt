package com.biologigor.snake.fx

import android.media.AudioManager
import android.media.ToneGenerator

/** Короткие сигналы через ToneGenerator — без звуковых файлов. */
class Beeper {
    private var tone: ToneGenerator? = null

    private fun generator(): ToneGenerator? {
        if (tone == null) {
            tone = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
            } catch (_: Exception) {
                null
            }
        }
        return tone
    }

    fun eat() {
        generator()?.startTone(ToneGenerator.TONE_PROP_BEEP, EAT_MS)
    }

    fun bonus() {
        generator()?.startTone(ToneGenerator.TONE_PROP_BEEP2, BONUS_MS)
    }

    fun levelUp() {
        generator()?.startTone(ToneGenerator.TONE_PROP_ACK, LEVEL_MS)
    }

    fun die() {
        generator()?.startTone(ToneGenerator.TONE_PROP_NACK, DIE_MS)
    }

    fun release() {
        tone?.release()
        tone = null
    }

    private companion object {
        const val VOLUME = 70
        const val EAT_MS = 70
        const val BONUS_MS = 120
        const val LEVEL_MS = 220
        const val DIE_MS = 320
    }
}
