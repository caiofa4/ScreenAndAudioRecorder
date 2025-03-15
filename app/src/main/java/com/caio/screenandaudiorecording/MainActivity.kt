package com.caio.screenandaudiorecording

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var isRecording = false
    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
    ).apply {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startScreenRecording()
        } else {
            Toast.makeText(this, "Permissions required to record screen", Toast.LENGTH_LONG).show()
        }
    }

    private val screenRecordLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = Intent(this, ScreenRecordingService::class.java).apply {
                action = "START"
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isRecording = true
            
            // Launch YouTube in a slight delay to ensure recording has started
            Handler(Looper.getMainLooper()).postDelayed({
                launchYouTube()
            }, 500)
        } else {
            isRecording = false
            Toast.makeText(this, "Screen recording permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var isRecordingState by remember { mutableStateOf(false) }
                    ScreenRecordingScreen(
                        isRecording = isRecordingState,
                        onStartRecording = { 
                            checkAndRequestPermissions()
                            isRecordingState = true
                        },
                        onStopRecording = { 
                            stopScreenRecording()
                            isRecordingState = false
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        var allPermissionsGranted = true

        // Check for All Files Access permission on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                allPermissionsGranted = false
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
                return // Wait for user to grant permission
            }
        } else {
            // For Android 10 and below
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // Check audio permission
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            allPermissionsGranted = false
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else if (allPermissionsGranted) {
            startScreenRecording()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if all files access was granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Check other permissions
                checkAndRequestPermissions()
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions granted, proceed with recording
                startScreenRecording()
            } else {
                // Handle permission denial
                Toast.makeText(this, "Permissions are required for recording", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startScreenRecording() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenRecordLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun stopScreenRecording() {
        val intent = Intent(this, ScreenRecordingService::class.java).apply {
            action = "STOP"
        }
        startService(intent)
        isRecording = false
    }

    private fun launchYouTube() {
        try {
            val youtubePackageName = "com.google.android.youtube"
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.component = packageManager.getLaunchIntentForPackage(youtubePackageName)?.component

            if (intent.component != null) {
                startActivity(intent)
            } else {
                // If component is null, try opening YouTube directly
                val youtubeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:"))
                youtubeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                youtubeIntent.setPackage(youtubePackageName)
                startActivity(youtubeIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Could not launch YouTube", Toast.LENGTH_SHORT).show()
            
            // Try opening YouTube in Play Store as fallback
            try {
                val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(playStoreIntent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Could not open Play Store", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun ScreenRecordingScreen(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isRecording) {
            Button(
                onClick = onStartRecording,
                modifier = Modifier.padding(8.dp)
            ) {
                Text("Start Recording")
            }
        } else {
            Button(
                onClick = onStopRecording,
                modifier = Modifier.padding(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop Recording")
            }
        }
    }
}