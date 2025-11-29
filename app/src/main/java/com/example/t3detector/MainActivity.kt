package com.example.t3detector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.t3detector.databinding.ActivityMainBinding
import com.example.t3detector.service.AudioDetectionService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStart.setOnClickListener {
            if (!hasAudioPermission()) {
                requestPermissions()
            } else {
                startDetectionService()
            }
        }

        binding.btnStop.setOnClickListener {
            stopDetectionService()
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startDetectionService()
        }

    private fun startDetectionService() {
        val intent = Intent(this, AudioDetectionService::class.java)
        startForegroundService(intent)
    }

    private fun stopDetectionService() {
        val intent = Intent(this, AudioDetectionService::class.java)
        stopService(intent)
    }
}
