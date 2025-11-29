package com.example.t3detector.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.t3detector.R
import com.example.t3detector.tflite.TfliteHelper
import kotlinx.coroutines.*
import kotlin.math.max

class AudioDetectionService : Service() {

    companion object {
        private const val CHANNEL_ID = "T3_DETECTOR_CHANNEL"
    }

    private val sampleRate = 16000
    // Fix: Model requires 3 seconds of audio (3 * 16000 = 48000 samples)
    private val recordingLength = 48000
    private lateinit var audioRecorder: AudioRecord
    private lateinit var tfliteHelper: TfliteHelper

    private var job: Job? = null
    private var isRunning = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(1, buildNotification("Listening for T3 alarm..."))
        isRunning = true

        // Ensure buffer is large enough for system requirements AND our 3s chunks
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        // Allocate buffer for 3 seconds of audio (2 bytes per sample)
        val bufferSizeInBytes = max(minBufferSize, recordingLength * 2)

        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeInBytes
        )

        tfliteHelper = TfliteHelper(this)

        job = CoroutineScope(Dispatchers.Default).launch { processAudioLoop() }
    }

    private suspend fun processAudioLoop() {
        // Buffer to hold 3 seconds of audio
        val shortBuffer = ShortArray(recordingLength)

        try {
            audioRecorder.startRecording()
            Log.d("T3Service", "Recording started (Buffer: $recordingLength samples)")

            while (isRunning) {
                // Read 3 seconds of audio (blocking)
                var readTotal = 0
                while (readTotal < recordingLength && isRunning) {
                    val read = audioRecorder.read(shortBuffer, readTotal, recordingLength - readTotal)
                    if (read < 0) {
                        Log.e("T3Service", "Audio read error: $read")
                        break
                    }
                    readTotal += read
                }

                if (readTotal < recordingLength) continue

                // Convert PCM16 -> Float
                val floatBuf = FloatArray(recordingLength)
                for (i in 0 until recordingLength) {
                    floatBuf[i] = shortBuffer[i] / 32768f
                }

                // Run inference
                val (label, conf) = tfliteHelper.runInference(floatBuf)
                Log.d("T3Service", "Detected: $label  conf=${"%.2f".format(conf)}")

                if (label == "T3" && conf > 0.85f) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(100, buildNotification("🚨 T3 Alarm DETECTED! (${"%.0f".format(conf * 100)}%)"))
                }
            }
        } catch (e: Exception) {
            Log.e("T3Service", "Error in audio loop", e)
        } finally {
            try {
                audioRecorder.stop()
                audioRecorder.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ... (rest of the file remains the same: stopDetection, onDestroy, onBind, etc.) ...

    fun stopDetection() {
        isRunning = false
        job?.cancel()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        job?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "T3 Detector Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("T3 Detector")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification) // Ensure this icon exists
            .setOngoing(true)
            .build()
    }
}