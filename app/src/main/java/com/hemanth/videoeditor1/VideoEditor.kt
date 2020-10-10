package com.hemanth.videoeditor1

import android.content.Context
import android.util.Log
import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler
import com.github.hiteshsondhi88.libffmpeg.FFmpeg
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException
import java.io.File
import java.io.IOException

class VideoEditor constructor(val context: Context) {
    var type: Int? = null
    var videoFile: File? = null
    var outputFilePath: String? = null
    var havingAudio: Boolean = true
    private var ffmpegFS: String? = null
    var callback: FFMpegCallback? = null

    fun setType(type: Int): VideoEditor {
        this.type = type
        return this
    }

    fun setFile(file: File): VideoEditor {
        this.videoFile = file
        return this
    }

    fun setOutputPath(outputPath: String): VideoEditor {
        this.outputFilePath = outputPath
        return this
    }

    fun setIsHavingAudio(havingAudio: Boolean): VideoEditor {
        this.havingAudio = havingAudio
        return this
    }

    fun setSpeedTempo(playbackSpeed: String, tempo: String): VideoEditor {
        this.ffmpegFS = if (havingAudio) "[0:v]setpts=$playbackSpeed*PTS[v];[0:a]atempo=$tempo[a]" else "setpts=$playbackSpeed*PTS"
        Log.v("Sample", "ffmpegFS: $ffmpegFS")
        return this
    }

    fun setCallback(callback: FFMpegCallback): VideoEditor {
        this.callback = callback
        return this
    }

    fun main() {
        val outputFile = File(outputFilePath)
        var cmd: Array<String>? = null
        when (type) {
            VideoConstants.VIDEO_PLAYBACK_SPEED -> {
                //Video playback speed - Need video file, speed & tempo value according to playback and output file
                cmd = if (havingAudio) {
                    arrayOf(
                        "-y",
                        "-i",
                        videoFile!!.path,
                        "-filter_complex",
                        ffmpegFS!!,
                        "-map",
                        "[v]",
                        "-map",
                        "[a]",
                        outputFile.path
                    )
                } else {
                    arrayOf("-y", "-i", videoFile!!.path, "-filter:v", ffmpegFS!!, outputFile.path)
                }
            }
        }

        try {
            FFmpeg.getInstance(context).execute(cmd, object : ExecuteBinaryResponseHandler() {
                override fun onStart() {

                }

                override fun onProgress(message: String?) {
                    callback!!.onProgress(message!!)
                }

                override fun onSuccess(message: String?) {
                    callback!!.onSuccess(outputFile, OptiOutputType.TYPE_VIDEO)
                }

                override fun onFailure(message: String?) {
                    if (outputFile.exists()) {
                        outputFile.delete()
                    }
                    callback!!.onFailure(IOException(message))
                }

                override fun onFinish() {
                    callback!!.onFinish()
                }
            })
        } catch (e: Exception) {
            callback!!.onFailure(e)
        } catch (e2: FFmpegCommandAlreadyRunningException) {
            callback!!.onNotAvailable(e2)
        }
    }

    companion object {
        fun with(context: Context): VideoEditor {
            return VideoEditor(context)
        }
    }
}