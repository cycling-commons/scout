package org.cyclingcommons.scout.karoo.tagging

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short buzz/tone on tag confirm; distinct pattern on undo (SPEC §6.11). */
class RideFeedback(context: Context) {
    private val app = context.applicationContext

    private val vibrator: Vibrator? by lazy {
        val v =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                app.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Vibrator::class.java)
            }
        v?.takeIf { it.hasVibrator() }
    }

    private val tones: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME) }.getOrNull()
    }

    fun confirm(undone: Boolean) {
        runCatching { vibrate(undone) }
        runCatching { playTone(undone) }
    }

    fun release() {
        runCatching { tones?.release() }
    }

    private fun vibrate(undone: Boolean) {
        val v = vibrator ?: return
        val effect =
            if (undone) {
                VibrationEffect.createWaveform(UNDO_PATTERN, -1)
            } else {
                VibrationEffect.createOneShot(CONFIRM_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            }
        v.vibrate(effect)
    }

    private fun playTone(undone: Boolean) {
        val generator = tones ?: return
        if (undone) {
            generator.startTone(ToneGenerator.TONE_CDMA_CONFIRM, UNDO_TONE_MS)
        } else {
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, CONFIRM_TONE_MS)
        }
    }

    private companion object {
        const val TONE_VOLUME = 80
        const val CONFIRM_MS = 120L
        const val CONFIRM_TONE_MS = 120
        const val UNDO_TONE_MS = 220
        val UNDO_PATTERN = longArrayOf(0, 90, 60, 90)
    }
}
