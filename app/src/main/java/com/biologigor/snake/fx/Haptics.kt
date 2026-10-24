package com.biologigor.snake.fx

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Вибрация с учётом API: на 31+ — через VibratorManager. */
class Haptics(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun light() = vibrate(25)

    fun medium() = vibrate(60)

    fun heavy() = vibrate(160)

    private fun vibrate(ms: Long) {
        if (!vibrator.hasVibrator()) return
        // SecurityException на некоторых прошивках не должен убивать игру
        try {
            vibrator.vibrate(
                VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) {
        }
    }
}
