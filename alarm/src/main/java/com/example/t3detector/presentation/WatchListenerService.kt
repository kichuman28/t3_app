package com.example.t3detector.presentation

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WatchListenerService : WearableListenerService() {

    @RequiresPermission(Manifest.permission.VIBRATE)
    override fun onMessageReceived(messageEvent: MessageEvent) {
        // The path "/trigger_t3_alarm" must match the one in Phone's WearableSender.kt
        if (messageEvent.path == "/trigger_t3_alarm") {
            Log.d("WatchListener", "🔥 Alarm signal received from Phone! Vibrating...")
            vibrateWatch()
        } else {
            super.onMessageReceived(messageEvent)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateWatch() {
        // Get the vibrator service (handles differences between Android versions)
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        // Define a "Heavy" vibration pattern:
        // [Delay, Vibrate, Sleep, Vibrate, Sleep, Vibrate]
        val pattern = longArrayOf(0, 500, 100, 500, 100, 500)

        // Vibrate
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // -1 means "do not repeat"
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }
}