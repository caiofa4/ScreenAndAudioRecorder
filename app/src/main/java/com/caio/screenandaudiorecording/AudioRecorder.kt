package com.caio.screenandaudiorecording

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorder(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var outputFile: File? = null

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT


    fun startRecording(): File {
        if (isRecording) {
            throw IllegalStateException("Already recording")
        }

//        val bufferSize = AudioRecord.getMinBufferSize(
//            44100,
//            AudioFormat.CHANNEL_IN_STEREO,
//            AudioFormat.ENCODING_PCM_16BIT
//        )

        try {
            // Create the playback capture configuration
//            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
//                .addMatchingUsage(AudioPlaybackCaptureConfiguration.MATCH_MODE_AUDIO_PLAYBACK)
//                .build()

            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA) // Captura áudio de mídia (música, vídeos, jogos)
                .build()

            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            // Create AudioRecord instance
//            audioRecord = AudioRecord.Builder()
//                .setAudioFormat(AudioFormat.Builder()
//                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
//                    .setSampleRate(44100)
//                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
//                    .build())
//                .setBufferSizeInBytes(bufferSize)
//                .setAudioPlaybackCaptureConfig(config)
//                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setAudioPlaybackCaptureConfig(config)
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("Failed to initialize AudioRecord")
            }

            outputFile = getOutputFile()
            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                try {
                    val outputStream = FileOutputStream(outputFile)
                    val buffer = ByteArray(bufferSize)

                    while (isRecording) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        if (read > 0) {
                            outputStream.write(buffer, 0, read)
                        }
                    }

                    outputStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                    isRecording = false
                }
            }
            recordingThread?.start()

            return outputFile!!
        } catch (e: Exception) {
            e.printStackTrace()
            throw IllegalStateException("Failed to start audio recording: ${e.message}")
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null

        isRecording = false
        try {
            recordingThread?.join()
            audioRecord?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
            recordingThread = null
        }
        return outputFile
    }

    private fun getOutputFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "audio_record_$timeStamp.pcm"
        
        // Use public external storage
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        return File(directory, fileName)
    }
} 