package com.caio.screenandaudiorecording

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.FileOutputStream

class AudioCaptureService : AccessibilityService() {
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for audio capture
    }

    override fun onInterrupt() {
        stopRecording()
    }

    fun startRecording(outputFile: File) {
        if (isRecording) return

        val bufferSize = AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.REMOTE_SUBMIX,
            44100,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            val outputStream = FileOutputStream(outputFile)
            val buffer = ByteArray(bufferSize)

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                }
            }

            outputStream.close()
        }
        recordingThread?.start()
    }

    fun stopRecording() {
        isRecording = false
        recordingThread?.join()
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        recordingThread = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopRecording()
        return super.onUnbind(intent)
    }
} 