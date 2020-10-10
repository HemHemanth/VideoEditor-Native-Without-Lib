package com.hemanth.videoeditor1

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object Utils {
    fun isVideoHavingAudio(path: String): Boolean {
        var audioTrack = false

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
        audioTrack = hasAudioStr == "yes"

        return audioTrack
    }

    fun createVideoFile(context: Context): File {
        val timeStamp: String = SimpleDateFormat(VideoConstants.DATE_FORMAT, Locale.getDefault()).format(
            Date()
        )
        val imageFileName: String = VideoConstants.APP_NAME + timeStamp + "_"
        val storageDir: File = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)!!
        if (!storageDir.exists()) storageDir.mkdirs()
        return File.createTempFile(imageFileName, VideoConstants.VIDEO_FORMAT, storageDir)
    }
}