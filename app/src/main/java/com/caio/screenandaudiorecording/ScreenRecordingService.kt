package com.caio.screenandaudiorecording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

class ScreenRecordingService : Service() {
    private val TAG = "ScreenRecordingService"
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioRecorder: AudioRecorder? = null
    private var isRecording = false
    private var currentVideoFile: File? = null
    private var currentAudioFile: File? = null

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "screen_record_channel"
        private const val NOTIFICATION_ID = 1
        private const val DISPLAY_WIDTH = 1080
        private const val DISPLAY_HEIGHT = 1920
    }

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopRecording()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val resultCode = intent.getIntExtra("resultCode", -1)
                val data = intent.getParcelableExtra<Intent>("data")
                startRecording(resultCode, data)
            }
            "STOP" -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Screen Recording",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Screen Recording")
            .setContentText("Recording in progress...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun startRecording(resultCode: Int, data: Intent?) {
        if (isRecording) return

        // Start foreground service immediately
        startForeground(NOTIFICATION_ID, createNotification())

        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data!!).apply {
            registerCallback(mediaProjectionCallback, null)
        }

        // Start audio recording with MediaProjection
        audioRecorder = AudioRecorder(applicationContext, mediaProjection!!)
        try {
            audioRecorder?.startRecording()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(8 * 1024 * 1024)
            setVideoFrameRate(30)
            setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT)
            setOutputFile(getOutputFile().absolutePath)
            prepare()
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecording",
            DISPLAY_WIDTH, DISPLAY_HEIGHT, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface, null, null
        )

        mediaRecorder?.start()
        isRecording = true
    }

    private fun stopRecording() {
        if (!isRecording) return

        var videoFile: File? = null
        var audioFile: File? = null

        try {
            audioFile = audioRecorder?.stopRecording()
            videoFile = currentVideoFile

            if (audioFile == null) {
                Log.i(TAG, "Audio file is null")
            }

            if (videoFile == null) {
                Log.i(TAG, "Video file is null")
            }

            mediaRecorder?.apply {
                stop()
                release()
            }
            virtualDisplay?.release()
            mediaProjection?.apply {
                unregisterCallback(mediaProjectionCallback)
                stop()
            }

            // Launch coroutine to merge files
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (videoFile != null && audioFile != null) {
                        val mergedFile = MediaMerger.mergeVideoAndAudio(videoFile, audioFile)
                        // Delete the separate files after successful merge
                        videoFile.delete()
                        audioFile.delete()
                        // Show success notification
                        showMergeSuccessNotification(mergedFile)
                    }
                } catch (e: Exception) {
                    Log.e("ScreenRecordingService", "Failed to merge media files", e)
                    // Show error notification
                    showMergeErrorNotification()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecorder = null
            mediaRecorder = null
            virtualDisplay = null
            mediaProjection = null
            currentVideoFile = null
            currentAudioFile = null
            isRecording = false
            stopForeground(true)
            stopSelf()
        }
    }

    private fun getOutputFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "ScreenRecording_$timestamp.mp4"
        
        // Use public external storage
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        val file = File(directory, filename)
        currentVideoFile = file
        return file
    }

    private fun showMergeSuccessNotification(outputFile: File) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Recording Complete")
            .setContentText("Video saved to: ${outputFile.name}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        notificationManager.notify(2, notification)
    }

    private fun showMergeErrorNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Recording Error")
            .setContentText("Failed to merge video and audio")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .build()
        notificationManager.notify(3, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }
} 