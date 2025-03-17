package com.caio.screenandaudiorecording

import android.os.Environment
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MediaMerger {
    companion object {
        private const val TAG = "MediaMerger"

        suspend fun mergeVideoAndAudio(videoFile: File, audioFile: File): File = suspendCoroutine { continuation ->
            // Use public external storage for merged output
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            
            if (!directory.exists()) {
                directory.mkdirs()
            }
            
            val outputFile = File(directory, "merged_${System.currentTimeMillis()}.mp4")
            
            // FFmpeg command to merge video and PCM audio
            // Specify PCM audio format: 44.1kHz, stereo, 16-bit
            val command = "-i ${videoFile.absolutePath} -f s16le -ar 44100 -ac 2 -i ${audioFile.absolutePath} " +
                         "-c:v copy -c:a aac -strict experimental " +
                         "-map 0:v:0 -map 1:a:0 " +
                         "-shortest ${outputFile.absolutePath}"
            
            Log.d(TAG, "Executing FFmpeg command: $command")
            
            FFmpegKit.executeAsync(command) { session ->
                when {
                    ReturnCode.isSuccess(session.returnCode) -> {
                        Log.d(TAG, "Media merge successful")
                        continuation.resume(outputFile)
                    }
                    ReturnCode.isCancel(session.returnCode) -> {
                        Log.e(TAG, "Media merge cancelled")
                        continuation.resumeWithException(Exception("Media merge cancelled"))
                    }
                    else -> {
                        val error = session.allLogsAsString
                        Log.e(TAG, "Media merge failed with state: ${session.state} and rc: ${session.returnCode}")
                        Log.e(TAG, "Error details: $error")
                        continuation.resumeWithException(Exception("Media merge failed: $error"))
                    }
                }
            }
        }
    }
} 