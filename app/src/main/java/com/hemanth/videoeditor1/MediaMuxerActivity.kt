package com.hemanth.videoeditor1

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import com.hemanth.videoeditor1.R
import kotlinx.android.synthetic.main.activity_media_muxer.*
import java.io.File
import java.nio.ByteBuffer


class MediaMuxerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_muxer)

        /*val file =
            File(
                Environment.getExternalStorageDirectory()
                    .toString() + File.separator.toString() + "my_video.mp4"
            )
        file.createNewFile()
        var outputFile: String = file.absolutePath*/

//        mix.setOnClickListener {
/*
            var format1: MediaFormat
            var format2: MediaFormat

            var mVideoTrackIndex = 0
            var mAudioTrackIndex = 0
            var frameRate1: Long = 0
            var frameRate2: Long = 0
            try {
                val file =
                    File(
                        Environment.getExternalStorageDirectory()
                            .toString() + File.separator.toString() + "my_video.mp4"
                    )
                file.createNewFile()
                var outputFile: String = file.absolutePath

                var muxer = MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                var videoExtractor = MediaExtractor()
                videoExtractor.setDataSource("/storage/emulated/0/WhatsApp/Media/WhatsApp Video/sample.mp4")

                for (i in 0 until videoExtractor.trackCount) {
                    format1 = videoExtractor.getTrackFormat(i)
                    var mime = format1.getString(MediaFormat.KEY_MIME)

                    if (mime != null) {
                        if (mime.startsWith("video")) {
                            videoExtractor.selectTrack(i)
                            frameRate1 = format1.getInteger(MediaFormat.KEY_FRAME_RATE).toLong()
                            mVideoTrackIndex = muxer.addTrack(format1)
                        }

                    }
                }

                var audioExtractor = MediaExtractor()
                audioExtractor.setDataSource("/storage/emulated/0/sample1.aac")

                for (i in 0 until audioExtractor.trackCount) {
                    format2 = audioExtractor.getTrackFormat(i)
                    var mime = format2.getString(MediaFormat.KEY_MIME)

                    if (mime != null) {
                        if (mime.startsWith("audio")) {
                            var buffer = ByteBuffer.allocate(100 * 1024).also {
                                audioExtractor.selectTrack(i)
                                audioExtractor.readSampleData(it, 0)
                                val first_sampletime: Long = audioExtractor.getSampleTime()
                                audioExtractor.advance()
                                val second_sampletime: Long = audioExtractor.getSampleTime()
                                frameRate2 = Math.abs(second_sampletime - first_sampletime) //timestamp

                                audioExtractor.unselectTrack(i)
                            }
                            audioExtractor.selectTrack(i)
                            mAudioTrackIndex = muxer.addTrack(format2)
                        }
                    }
                }

                muxer.start()

                var info = MediaCodec.BufferInfo()
                info.presentationTimeUs = 0
                var buffer = ByteBuffer.allocate(100 * 1024)
                var sampleSize1 = 0

                while ((videoExtractor.readSampleData(buffer, 0).also { sampleSize1 = it }) > 0) {
                    info.offset = 0;
                    info.size = sampleSize1;
                    info.flags = videoExtractor.getSampleFlags();
                    info.presentationTimeUs += 1000 * 1000 / frameRate1;
                    muxer.writeSampleData(mVideoTrackIndex, buffer, info);
                    videoExtractor.advance();
                }

                var info1 = MediaCodec.BufferInfo()
                info1.presentationTimeUs = 0
                var sampleSize2 = 0

                while ((audioExtractor.readSampleData(buffer, 0).also { sampleSize2  = it}) > 0) {
                    info1.offset = 0;
                    info1.size = sampleSize2;
                    info1.flags = audioExtractor.getSampleFlags();
                    info1.presentationTimeUs += frameRate2;
                    muxer.writeSampleData(mAudioTrackIndex, buffer, info1);
                    audioExtractor.advance();
                }

                videoExtractor.release()
                audioExtractor.release()

                muxer.stop()
                muxer.release()
            } catch (ioe: IOException) {
                ioe.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
*/

//        }

        mix.setOnClickListener {
            var mediaMuserFile = File(Environment.getExternalStorageDirectory()
                .toString() + "/Media Muxer/")
            if (!mediaMuserFile.exists()) {
                mediaMuserFile.mkdirs()
            }
            val file =
                File(
                    Environment.getExternalStorageDirectory()
                        .toString() + "/Media Muxer/" + "my_video.mp4"
                )
            file.createNewFile()
            var outputFile: String = file.absolutePath

            var videoFormat: MediaFormat? = null
            var videoTrackIndex: Int = 0
            var audioTrackIndex: Int = 0
            var frameMaxInputSize: Int = 0
            var frameRate: Int = 0
            var videoDuration: Long = 0

            var videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoPath.text.toString())

            var mediaMuxer: MediaMuxer? = null

            for (i in 0 until videoExtractor.trackCount) {
                videoFormat = videoExtractor.getTrackFormat(i)
                var mimeType = videoFormat.getString(MediaFormat.KEY_MIME)
                if (mimeType != null) {
                    if (mimeType.startsWith("video")) {
                        videoTrackIndex = i
                        frameMaxInputSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        frameRate = videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
                        videoDuration = videoFormat.getLong(MediaFormat.KEY_DURATION)
                        break
                    }
                }
            }
            if (videoTrackIndex < 0)
                return@setOnClickListener

            var audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(audioPath.text.toString())
            var audioFormat: MediaFormat? = null

            for (i in 0 until audioExtractor.trackCount) {
                audioFormat = audioExtractor.getTrackFormat(i)
                var mimeType = audioFormat.getString(MediaFormat.KEY_MIME)
                if (mimeType != null) {
                    if (mimeType.startsWith("audio")) {
                        audioTrackIndex = i
                        break
                    }
                }
            }

            if (audioTrackIndex < 0) {
                return@setOnClickListener
            }

            var videoBufferInfo = MediaCodec.BufferInfo()
            mediaMuxer = MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            mediaMuxer.setOrientationHint(180)
            var writeVideoTrackIndex: Int? = videoFormat?.let { it1 -> mediaMuxer.addTrack(it1) }
            var writeAudioTrackIndex: Int? = audioFormat?.let { it1 -> mediaMuxer.addTrack(it1) }
            mediaMuxer.start()

            var byteBuffer = ByteBuffer.allocate(frameMaxInputSize)
            videoExtractor.unselectTrack(videoTrackIndex)
            videoExtractor.selectTrack(videoTrackIndex)

            while (true) {
                var readVideoSampleSize = videoExtractor.readSampleData(byteBuffer, 0)
                if (readVideoSampleSize < 0) {
                    videoExtractor.unselectTrack(videoTrackIndex);
                    break;
                }
                val videoSampleTime = videoExtractor.sampleTime
                videoBufferInfo.size = readVideoSampleSize
                videoBufferInfo.presentationTimeUs = videoSampleTime
                //videoBufferInfo.presentationTimeUs += 1000 * 1000 / frameRate;
                //videoBufferInfo.presentationTimeUs += 1000 * 1000 / frameRate;
                videoBufferInfo.offset = 0
                videoBufferInfo.flags = videoExtractor.sampleFlags
                mediaMuxer.writeSampleData(writeVideoTrackIndex!!, byteBuffer, videoBufferInfo)
                videoExtractor.advance()
            }

            var audioPresentationTimeUs: Long = 0
            val audioBufferInfo = MediaCodec.BufferInfo()
            audioExtractor.selectTrack(audioTrackIndex)
            /*
     * the last audio presentation time.
     */
            /*
     * the last audio presentation time.
     */
            var lastEndAudioTimeUs: Long = 0
            while (true) {
                val readAudioSampleSize = audioExtractor.readSampleData(byteBuffer, 0)
                if (readAudioSampleSize < 0) {
                    //if end of the stream, unselect
                    audioExtractor.unselectTrack(audioTrackIndex)
                    if (audioPresentationTimeUs >= videoDuration) {
                        //if has reach the end of the video time ,just exit
                        break
                    } else {
                        //if not the end of the video time, just repeat.
                        lastEndAudioTimeUs += audioPresentationTimeUs
                        audioExtractor.selectTrack(audioTrackIndex)
                        continue
                    }
                }
                val audioSampleTime = audioExtractor.sampleTime
                audioBufferInfo.size = readAudioSampleSize
                audioBufferInfo.presentationTimeUs = audioSampleTime + lastEndAudioTimeUs
                if (audioBufferInfo.presentationTimeUs > videoDuration) {
                    audioExtractor.unselectTrack(audioTrackIndex)
                    break
                }
                audioPresentationTimeUs = audioBufferInfo.presentationTimeUs
                audioBufferInfo.offset = 0
                audioBufferInfo.flags = audioExtractor.sampleFlags
                mediaMuxer.writeSampleData(writeAudioTrackIndex!!, byteBuffer, audioBufferInfo)
                audioExtractor.advance()
            }

            try {
                mediaMuxer.stop()
                mediaMuxer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                videoExtractor.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                audioExtractor.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }


    }
}
