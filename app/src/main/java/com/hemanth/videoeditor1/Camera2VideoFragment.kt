/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2video

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
import android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION
import android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
import android.hardware.camera2.CameraDevice.TEMPLATE_RECORD
import android.media.*
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.hemanth.videoeditor1.*
import com.hemanth.videoeditor1.AutoFitTextureView
import com.hemanth.videoeditor1.CompareSizesByArea
import com.hemanth.videoeditor1.ConfirmationDialog
import com.hemanth.videoeditor1.ErrorDialog
import kotlinx.android.synthetic.main.fragment_camera2_video.*
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class Camera2VideoFragment : Fragment(), View.OnClickListener,
    ActivityCompat.OnRequestPermissionsResultCallback,
    FFMpegCallback{

    private var cameraId = CAMERA_BACK
    var flashSupported: Boolean = false
    var flashStatus: Boolean = false
    var outputFile: String? = null
    var masterFile: File? = null
    var isHavingAudio: Boolean = true

    private val FRAGMENT_DIALOG = "dialog"
    private val TAG = "Camera2VideoFragment"
    private val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
    private val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
    private val DEFAULT_ORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 90)
        append(Surface.ROTATION_90, 0)
        append(Surface.ROTATION_180, 270)
        append(Surface.ROTATION_270, 180)
    }
    private val INVERSE_ORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 270)
        append(Surface.ROTATION_90, 180)
        append(Surface.ROTATION_180, 90)
        append(Surface.ROTATION_270, 0)
    }

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture) = true

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

    }

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private lateinit var textureView: AutoFitTextureView

    /**
     * Button to record video
     */
    private lateinit var videoButton: Button

    /**
     * A reference to the opened [android.hardware.camera2.CameraDevice].
     */
    private var cameraDevice: CameraDevice? = null

    /**
     * A reference to the current [android.hardware.camera2.CameraCaptureSession] for
     * preview.
     */
    private var captureSession: CameraCaptureSession? = null

    /**
     * The [android.util.Size] of camera preview.
     */
    private lateinit var previewSize: Size

    /**
     * The [android.util.Size] of video recording.
     */
    private lateinit var videoSize: Size

    /**
     * Whether the app is recording video now
     */
    private var isRecordingVideo = false

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var backgroundHandler: Handler? = null

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    /**
     * Orientation of the camera sensor
     */
    private var sensorOrientation = 0

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its status.
     */
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@Camera2VideoFragment.cameraDevice = cameraDevice
            startPreview()
            configureTransform(textureView.width, textureView.height)
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@Camera2VideoFragment.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@Camera2VideoFragment.cameraDevice = null
            activity?.finish()
        }

    }

    /**
     * Output file for video
     */
    private var nextVideoAbsolutePath: String? = null

    private var mediaRecorder: MediaRecorder? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera2_video, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById(R.id.texture)
        videoButton = view.findViewById<Button>(R.id.video).also {
            it.setOnClickListener(this)
        }

        var mediaMuxerFile = File(
            Environment.getExternalStorageDirectory()
                .toString() + "/Media Muxer/")
        if (!mediaMuxerFile.exists()) {
            mediaMuxerFile.mkdirs()
        }
        val file =
            File(
                Environment.getExternalStorageDirectory()
                    .toString() + "/Media Muxer/" + "my_video.mp4"
            )
        file.createNewFile()
        outputFile = file.absolutePath
        masterFile = file
//        view.findViewById<View>(R.id.info).setOnClickListener(this)

        switchCam.setOnClickListener {
            switchCamera()
        }

        flash.setOnClickListener {
            turnOnFlash()
        }

        aqua.setOnClickListener {
            previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_AQUA)
            captureSession?.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
        }

        sepia.setOnClickListener {
            previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_SEPIA)
            captureSession?.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
        }

        mono.setOnClickListener {
            previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_MONO)
            captureSession?.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
        }

        off.setOnClickListener {
            previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_OFF)
            captureSession?.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
        }

    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.video -> if (isRecordingVideo) stopRecordingVideo() else startRecordingVideo()
            R.id.info -> {
                if (activity != null) {
                    AlertDialog.Builder(activity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                }
            }
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = backgroundThread?.looper?.let { Handler(it) }
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private fun shouldShowRequestPermissionRationale(permissions: Array<String>) =
            permissions.any { shouldShowRequestPermissionRationale(it) }

    /**
     * Requests permissions needed for recording video.
     */
    private fun requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
            ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
        } else {
            requestPermissions(VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            if (grantResults.size == VIDEO_PERMISSIONS.size) {
                for (result in grantResults) {
                    if (result != PERMISSION_GRANTED) {
                        ErrorDialog.newInstance(getString(R.string.permission_request))
                                .show(childFragmentManager, FRAGMENT_DIALOG)
                        break
                    }
                }
            } else {
                ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(childFragmentManager, FRAGMENT_DIALOG)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun hasPermissionsGranted(permissions: Array<String>) =
            permissions.none {
                checkSelfPermission((activity as FragmentActivity), it) != PERMISSION_GRANTED
            }

    /**
     * Tries to open a [CameraDevice]. The result is listened by [stateCallback].
     *
     * Lint suppression - permission is checked in [hasPermissionsGranted]
     */
    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions()
            return
        }

        val cameraActivity = activity
        if (cameraActivity == null || cameraActivity.isFinishing) return

        val manager = cameraActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
//            val cameraId = manager.cameraIdList[0]

            // Choose the sizes for camera preview and video recording
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP) ?:
                    throw RuntimeException("Cannot get available preview/video sizes")
            sensorOrientation = characteristics.get(SENSOR_ORIENTATION)!!
            flashSupported = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                    width, height, videoSize)

            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                textureView.setAspectRatio(previewSize.width, previewSize.height)
            } else {
                textureView.setAspectRatio(previewSize.height, previewSize.width)
            }
            configureTransform(width, height)
            mediaRecorder = MediaRecorder()
            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            showToast("Cannot access the camera.")
            cameraActivity.finish()
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(childFragmentManager, FRAGMENT_DIALOG)
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.")
        }
    }

    /**
     * Close the [CameraDevice].
     */
    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            closePreviewSession()
            cameraDevice?.close()
            cameraDevice = null
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /**
     * Start the camera preview.
     */
    private fun startPreview() {
        if (cameraDevice == null || !textureView.isAvailable) return

        try {
            closePreviewSession()
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(previewSize.width, previewSize.height)
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(TEMPLATE_PREVIEW)

            val previewSurface = Surface(texture)
            previewRequestBuilder.addTarget(previewSurface)

            cameraDevice?.createCaptureSession(listOf(previewSurface),
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            updatePreview()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            if (activity != null) showToast("Failed")
                        }
                    }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    /**
     * Update the camera preview. [startPreview] needs to be called in advance.
     */
    private fun updatePreview() {
        if (cameraDevice == null) return

        try {
            setUpCaptureRequestBuilder(previewRequestBuilder)
            HandlerThread("CameraPreview").start()
            captureSession?.setRepeatingRequest(previewRequestBuilder.build(),
                    null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    private fun setUpCaptureRequestBuilder(builder: CaptureRequest.Builder?) {
        builder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        activity ?: return
        val rotation = (activity as FragmentActivity).windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / previewSize.height,
                    viewWidth.toFloat() / previewSize.width)
            with(matrix) {
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        }
        textureView.setTransform(matrix)
    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        val cameraActivity = activity ?: return

        if (nextVideoAbsolutePath.isNullOrEmpty()) {
            nextVideoAbsolutePath = getVideoFilePath(cameraActivity)
        }

        val rotation = cameraActivity.windowManager.defaultDisplay.rotation
        when (sensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES ->
                mediaRecorder?.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
            SENSOR_ORIENTATION_INVERSE_DEGREES ->
                mediaRecorder?.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
        }

        mediaRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(nextVideoAbsolutePath)
            setVideoEncodingBitRate(10000000)
            setVideoFrameRate(30)
            setVideoSize(videoSize.width, videoSize.height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }
    }

    private fun getVideoFilePath(context: Context?): String {
        val filename = "${System.currentTimeMillis()}.mp4"
        val dir = context?.getExternalFilesDir(null)

        return if (dir == null) {
            filename
        } else {
            "${dir.absolutePath}/$filename"
        }
    }

    private fun startRecordingVideo() {
        if (cameraDevice == null || !textureView.isAvailable) return

        try {
            closePreviewSession()
            setUpMediaRecorder()
            val texture = textureView.surfaceTexture?.apply {
                setDefaultBufferSize(previewSize.width, previewSize.height)
            }

            // Set up Surface for camera preview and MediaRecorder
            val previewSurface = Surface(texture)
            val recorderSurface = mediaRecorder!!.surface
            val surfaces = ArrayList<Surface>().apply {
                add(previewSurface)
                add(recorderSurface)
            }
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(TEMPLATE_RECORD).apply {
                addTarget(previewSurface)
                addTarget(recorderSurface)
            }

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            cameraDevice?.createCaptureSession(surfaces,
                    object : CameraCaptureSession.StateCallback() {

                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    captureSession = cameraCaptureSession
                    updatePreview()
                    activity?.runOnUiThread {
                        videoButton.setText(R.string.stop)
                        isRecordingVideo = true
                        mediaRecorder?.start()
                    }
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    if (activity != null) showToast("Failed")
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
        }

    }

    private fun closePreviewSession() {
        captureSession?.close()
        captureSession = null
    }

    private fun stopRecordingVideo() {
        isRecordingVideo = false
        videoButton.setText(R.string.record)
        mediaRecorder?.apply {
            stop()
            reset()
        }

        outputFile?.let { nextVideoAbsolutePath?.let { it1 -> mux(it1, "/storage/emulated/0/sample1.m4a", it) } }

//        if (activity != null) showToast("Video saved: $nextVideoAbsolutePath")
        nextVideoAbsolutePath = null
        startPreview()
    }

    private fun showToast(message : String) = Toast.makeText(activity, message, LENGTH_SHORT).show()

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private fun chooseVideoSize(choices: Array<Size>) = choices.firstOrNull {
        it.width == it.height * 4 / 3 && it.width <= 1080 } ?: choices[choices.size - 1]

    /**
     * Given [choices] of [Size]s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal [Size], or an arbitrary one if none were big enough
     */
    private fun chooseOptimalSize(
            choices: Array<Size>,
            width: Int,
            height: Int,
            aspectRatio: Size
    ): Size {

        // Collect the supported resolutions that are at least as big as the preview Surface
        val w = aspectRatio.width
        val h = aspectRatio.height
        val bigEnough = choices.filter {
            it.height == it.width * h / w && it.width >= width && it.height >= height }

        // Pick the smallest of those, assuming we found any
        return if (bigEnough.isNotEmpty()) {
            Collections.min(bigEnough, CompareSizesByArea())
        } else {
            choices[0]
        }
    }

    private fun switchCamera() {
        if (cameraId == CAMERA_FRONT) {
            /*if (flashStatus && cameraId == "0" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager?.setTorchMode(cameraId, false)
            }*/
            cameraId = CAMERA_BACK
            closeCamera()
            reOpenCamera()
            flashStatus = false
        } else if (cameraId == CAMERA_BACK) {
            cameraId = CAMERA_FRONT
            closeCamera()
            reOpenCamera()
        }
    }

    private fun turnOnFlash() {
        try {
            /*if (!flashStatus && cameraId == "0" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager?.setTorchMode(cameraId, true)
            }*/

            if (flashSupported) {
                if (!flashStatus) {
//                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    previewRequestBuilder.set(
                        CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_TORCH
                    )
                    captureSession?.setRepeatingRequest(
                        previewRequestBuilder.build(),
                        null,
                        backgroundHandler
                    )
                    flashStatus = true
                } else {
                    previewRequestBuilder.set(
                        CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF
                    )
                    captureSession?.setRepeatingRequest(
                        previewRequestBuilder.build(),
                        null,
                        backgroundHandler
                    )
                    flashStatus = false
                }
            }
        } catch (cae: CameraAccessException) {
            cae.printStackTrace()
        }
    }
    private fun reOpenCamera() {
        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    private fun mux(videoPath: String, audioPath: String, outPutFilePath: String) {
        var videoFormat: MediaFormat? = null
        var videoTrackIndex: Int = 0
        var audioTrackIndex: Int = 0
        var frameMaxInputSize: Int = 0
        var frameRate: Int = 0
        var videoDuration: Long = 0

        var videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoPath)

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
            return

        var audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(audioPath)
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
            return
        }

        var videoBufferInfo = MediaCodec.BufferInfo()
        mediaMuxer = outputFile?.let { MediaMuxer(it, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) }
//        mediaMuxer.setOrientationHint(180)
        var writeVideoTrackIndex: Int? = videoFormat?.let { it1 -> mediaMuxer?.addTrack(it1) }
        var writeAudioTrackIndex: Int? = audioFormat?.let { it1 -> mediaMuxer?.addTrack(it1) }
        val rotation = activity?.windowManager?.defaultDisplay?.rotation!!
        when (sensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES ->
                mediaMuxer?.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
            SENSOR_ORIENTATION_INVERSE_DEGREES ->
                mediaMuxer?.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
        }
        mediaMuxer?.start()

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
            mediaMuxer?.writeSampleData(writeVideoTrackIndex!!, byteBuffer, videoBufferInfo)
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
            mediaMuxer?.writeSampleData(writeAudioTrackIndex!!, byteBuffer, audioBufferInfo)
            audioExtractor.advance()
        }

        try {
            mediaMuxer?.stop()
            mediaMuxer?.release()
            isHavingAudio = outputFile?.let { Utils.isVideoHavingAudio(it) }!!
            processVideo("0.50", "0.50")
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

    private fun processVideo(playbackSpeed: String, tempo: String) {
        if(playbackSpeed != "0.0") {
            //output file is generated and send to video processing
            val outputFile = Utils.createVideoFile(context!!)
            Log.v("Sample", "outputFile: ${outputFile.absolutePath}")

            VideoEditor.with(context!!)
                .setType(VideoConstants.VIDEO_PLAYBACK_SPEED)
                .setFile(masterFile!!)
                .setOutputPath(outputFile.absolutePath)
                .setIsHavingAudio(isHavingAudio)
                .setSpeedTempo(playbackSpeed, tempo)
                .setCallback(this)
                .main()

//            helper?.showLoading(true)
//            dismiss()
        } else {
//            OptiUtils.showGlideToast(activity!!, getString(R.string.error_select_speed))
        }
    }

    companion object {
        fun newInstance(): Camera2VideoFragment = Camera2VideoFragment()
        const val CAMERA_FRONT = "1"
        const val CAMERA_BACK = "0"
    }

    override fun onProgress(progress: String) {

    }

    override fun onSuccess(convertedFile: File, type: String) {

    }

    override fun onFailure(error: Exception) {

    }

    override fun onNotAvailable(error: Exception) {

    }

    override fun onFinish() {

    }

}
