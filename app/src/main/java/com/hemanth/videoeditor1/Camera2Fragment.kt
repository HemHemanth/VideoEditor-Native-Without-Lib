package com.hemanth.videoeditor1

import android.app.Fragment
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.*
import android.media.ImageReader.OnImageAvailableListener
import android.net.Uri
import android.os.*
import android.util.Log
import android.util.Size
import android.view.*
import android.view.TextureView.SurfaceTextureListener
import android.view.View.OnTouchListener
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import com.hemanth.videoeditor1.R
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class Camera2Fragment : Fragment() {
    var mTextureView: TextureView? = null
    var mThumbnail: ImageView? = null
    var mButton: Button? = null
    var mHandler: Handler? = null
    var mUIHandler: Handler? = null
    var mImageReader: ImageReader? = null
    var mPreViewBuidler: CaptureRequest.Builder? = null
    var mCameraSession: CameraCaptureSession? = null
    var mCameraCharacteristics: CameraCharacteristics? = null
    var ringtone: Ringtone? = null

    //相机会话的监听器，通过他得到mCameraSession对象，这个对象可以用来发送预览和拍照请求
    private val mSessionStateCallBack: CameraCaptureSession.StateCallback =
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                try {
                    mCameraSession = cameraCaptureSession
                    cameraCaptureSession.setRepeatingRequest(
                        mPreViewBuidler!!.build(),
                        null,
                        mHandler
                    )
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            }

            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {}
        }
    private var surface: Surface? = null

    //打开相机时候的监听器，通过他可以得到相机实例，这个实例可以创建请求建造者
    private val cameraOpenCallBack: CameraDevice.StateCallback =
        object : CameraDevice.StateCallback() {
            override fun onOpened(cameraDevice: CameraDevice) {
                Log.d(TAG, "相机已经打开")
                try {
                    mPreViewBuidler =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    val texture = mTextureView!!.surfaceTexture
                    texture!!.setDefaultBufferSize(mPreViewSize!!.width, mPreViewSize!!.height)
                    surface = Surface(texture)
                    mPreViewBuidler!!.addTarget(surface!!)
                    cameraDevice.createCaptureSession(
                        Arrays.asList(
                            surface,
                            mImageReader!!.surface
                        ), mSessionStateCallBack, mHandler
                    )
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            }

            override fun onDisconnected(cameraDevice: CameraDevice) {
                Log.d(TAG, "相机连接断开")
            }

            override fun onError(cameraDevice: CameraDevice, i: Int) {
                Log.d(TAG, "相机打开失败")
            }
        }
    private val onImageAvaiableListener =
        OnImageAvailableListener { imageReader -> mHandler!!.post(ImageSaver(imageReader.acquireNextImage())) }
    private var mPreViewSize: Size? = null
    private var maxZoomrect: Rect? = null
    private var maxRealRadio = 0
    private var mSensorOrientation: Int? = null

    //预览图显示控件的监听器，可以监听这个surface的状态
    private val mSurfacetextlistener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, i: Int, i1: Int) {
            val thread = HandlerThread("Ceamera3")
            thread.start()
            mHandler = Handler(thread.looper)
            val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraid = CameraCharacteristics.LENS_FACING_BACK.toString() + ""
            try {
                mCameraCharacteristics = manager.getCameraCharacteristics(cameraid)

                //画面传感器的面积，单位是像素。
                maxZoomrect =
                    mCameraCharacteristics!!.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                //最大的数字缩放
                maxRealRadio =
                    mCameraCharacteristics!!.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!
                        .toInt()
                picRect = Rect(maxZoomrect)
                val map =
                    mCameraCharacteristics!!.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val largest = Collections.max(
                    Arrays.asList(
                        *map!!.getOutputSizes(ImageFormat.JPEG)
                    ), CompareSizeByArea()
                )
                mPreViewSize = map.getOutputSizes(SurfaceTexture::class.java)[0]
                choosePreSize(i, i1, map, largest)
                mImageReader = ImageReader.newInstance(
                    largest.width, largest.height,
                    ImageFormat.JPEG, 5
                )
                mImageReader!!.setOnImageAvailableListener(onImageAvaiableListener, mHandler)
                manager.openCamera(cameraid, cameraOpenCallBack, mHandler)
                //设置点击拍照的监听
//                mButton.setOnTouchListener(onTouchListener);
                mButton!!.setOnTouchListener(onTouchListener)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }

        private fun choosePreSize(i: Int, i1: Int, map: StreamConfigurationMap?, largest: Size) {
            val displayRotation = activity.windowManager.defaultDisplay.rotation
            mSensorOrientation =
                mCameraCharacteristics!!.get(CameraCharacteristics.SENSOR_ORIENTATION)
            var swappedDimensions = false
            when (displayRotation) {
                Surface.ROTATION_0, Surface.ROTATION_180 -> if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    swappedDimensions = true
                }
                Surface.ROTATION_90, Surface.ROTATION_270 -> if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                    swappedDimensions = true
                }
                else -> Log.e(TAG, "Display rotation is invalid: $displayRotation")
            }
            val displaySize = Point()
            activity.windowManager.defaultDisplay.getSize(displaySize)
            var rotatedPreviewWidth = i
            var rotatedPreviewHeight = i1
            var maxPreviewWidth = displaySize.x
            var maxPreviewHeight = displaySize.y
            if (swappedDimensions) {
                rotatedPreviewWidth = i1
                rotatedPreviewHeight = i
                maxPreviewWidth = displaySize.y
                maxPreviewHeight = displaySize.x
            }
            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH
            }
            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT
            }
            mPreViewSize = chooseOptimalSize(
                map!!.getOutputSizes(SurfaceTexture::class.java),
                rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                maxPreviewHeight, largest
            )
        }

        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, i: Int, i1: Int) {}
        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
        private fun chooseOptimalSize(
            choices: Array<Size>, textureViewWidth: Int,
            textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size
        ): Size {

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough: MutableList<Size> = ArrayList()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough: MutableList<Size> = ArrayList()
            val w = aspectRatio.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * 3 / 4) {
                    if (option.width >= textureViewWidth &&
                        option.height >= textureViewHeight
                    ) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            return if (bigEnough.size > 0) {
                Collections.min(bigEnough, CompareSizeByArea())
            } else if (notBigEnough.size > 0) {
                Collections.max(notBigEnough, CompareSizeByArea())
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size")
                choices[0]
            }
        }
    }
    private val onTouchListener = OnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> try {
                mCameraSession!!.setRepeatingRequest(initDngBuilder()!!.build(), null, mHandler)
            } catch (e: CameraAccessException) {
                Toast.makeText(activity, "请求相机权限被拒绝", Toast.LENGTH_SHORT).show()
            }
            MotionEvent.ACTION_UP -> try {
                updateCameraPreviewSession()
            } catch (e: CameraAccessException) {
                Toast.makeText(activity, "请求相机权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
        true
    }

    @Throws(CameraAccessException::class)
    private fun updateCameraPreviewSession() {
        mPreViewBuidler!!.set(
            CaptureRequest.CONTROL_AF_TRIGGER,
            CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
        )
        mPreViewBuidler!!.set(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
        )
        mCameraSession!!.setRepeatingRequest(mPreViewBuidler!!.build(), null, mHandler)
    }

    /**
     * 设置连拍的参数
     *
     * @return
     */
    private fun initDngBuilder(): CaptureRequest.Builder? {
        var captureBuilder: CaptureRequest.Builder? = null
        try {
            captureBuilder =
                mCameraSession!!.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureBuilder.addTarget(mImageReader!!.surface)
            captureBuilder.addTarget(surface!!)
            // Required for RAW capture
            captureBuilder.set(
                CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON
            )
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            captureBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            )
            captureBuilder.set(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                ((214735991 - 13231) / 2).toLong()
            )
            captureBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0)
            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, (10000 - 100) / 2) //设置 ISO，感光度
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90)
            //设置每秒30帧
            val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraid = CameraCharacteristics.LENS_FACING_FRONT.toString() + ""
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraid)
            val fps =
                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)!!
            captureBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps[fps.size - 1])
        } catch (e: CameraAccessException) {
            Toast.makeText(activity, "请求相机权限被拒绝", Toast.LENGTH_SHORT).show()
        } catch (e: NullPointerException) {
            Toast.makeText(activity, "打开相机失败", Toast.LENGTH_SHORT).show()
        }
        return captureBuilder
    }

    private val picOnClickListener = View.OnClickListener {
        try {
            shootSound()
            Log.d(TAG, "正在拍照")
            val builder =
                mCameraSession!!.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            builder.addTarget(mImageReader!!.surface)
            builder.set(CaptureRequest.SCALER_CROP_REGION, picRect)
            builder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_EDOF
            )
            //                builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
//                        CameraMetadata.CONTROL_AF_TRIGGER_START);
            builder.set(CaptureRequest.JPEG_ORIENTATION, 90)
            mCameraSession!!.capture(builder.build(), null, mHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
    private val textTureOntuchListener: OnTouchListener = object : OnTouchListener {
        //时时当前的zoom
        var zoom = 0.0

        // 0<缩放比<mCameraCharacteristics.get(CameraCharacteristics
        // .SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).intValue();
        //上次缩放前的zoom
        var lastzoom = 0.0

        //两个手刚一起碰到手机屏幕的距离
        var lenth = 0.0
        var count = 0
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> count = 1
                MotionEvent.ACTION_MOVE -> if (count >= 2) {
                    val x1 = event.getX(0)
                    val y1 = event.getY(0)
                    val x2 = event.getX(1)
                    val y2 = event.getY(1)
                    val x = x1 - x2
                    val y = y1 - y2
                    val lenthRec = Math.sqrt(x * x + y * y.toDouble()) - lenth
                    val viewLenth = Math.sqrt(
                        v.width * v.width + v.height
                                * v.height.toDouble()
                    )
                    zoom = lenthRec / viewLenth * maxRealRadio + lastzoom
                    picRect!!.top = (maxZoomrect!!.top / zoom).toInt()
                    picRect!!.left = (maxZoomrect!!.left / zoom).toInt()
                    picRect!!.right = (maxZoomrect!!.right / zoom).toInt()
                    picRect!!.bottom = (maxZoomrect!!.bottom / zoom).toInt()
                    Message.obtain(mUIHandler, MOVE_FOCK).sendToTarget()
                }
                MotionEvent.ACTION_UP -> count = 0
                MotionEvent.ACTION_POINTER_DOWN -> {
                    count++
                    if (count == 2) {
                        val x1 = event.getX(0)
                        val y1 = event.getY(0)
                        val x2 = event.getX(1)
                        val y2 = event.getY(1)
                        val x = x1 - x2
                        val y = y1 - y2
                        lenth = Math.sqrt(x * x + y * y.toDouble())
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    count--
                    if (count < 2) lastzoom = zoom
                }
            }
            return true
        }
    }

    //相机缩放相关
    private var picRect: Rect? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle
    ): View? {
        val v = inflater.inflate(R.layout.fragment_camera2, null)
        //        findview(v);
        mUIHandler = Handler(InnerCallBack())
        //初始化拍照的声音
        ringtone = RingtoneManager.getRingtone(
            activity,
            Uri.parse("file:///system/media/audio/ui/camera_click.ogg")
        )
        val attr = AudioAttributes.Builder()
        attr.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
//        ringtone.setAudioAttributes(attr.build())
        //初始化相机布局
        mTextureView!!.surfaceTextureListener = mSurfacetextlistener
        mTextureView!!.setOnTouchListener(textTureOntuchListener)
        return v
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (mCameraSession != null) {
            mCameraSession!!.device.close()
            mCameraSession!!.close()
        }
    }
    /*private void findview(View v) {
        mTextureView = (TextureView) v.findViewById(R.id.tv_textview);
        mButton = (Button) v.findViewById(R.id.btn_takepic);
        mThumbnail = (ImageView) v.findViewById(R.id.iv_Thumbnail);
        mThumbnail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getActivity(), "别戳了，那个页面还没写", Toast.LENGTH_SHORT).show();
            }
        });
    }*/
    /**
     * 播放系统的拍照的声音
     */
    fun shootSound() {
        ringtone!!.stop()
        ringtone!!.play()
    }

    private inner class ImageSaver(var reader: Image?) : Runnable {
        override fun run() {
            Log.d(TAG, "正在保存图片")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                .absoluteFile
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, System.currentTimeMillis().toString() + ".jpg")
            var outputStream: FileOutputStream? = null
            try {
                outputStream = FileOutputStream(file)
                val buffer = reader!!.planes[0].buffer
                val buff = ByteArray(buffer.remaining())
                buffer[buff]
                val ontain = BitmapFactory.Options()
                ontain.inSampleSize = 50
                val bm = BitmapFactory.decodeByteArray(buff, 0, buff.size, ontain)
                Message.obtain(mUIHandler, SETIMAGE, bm).sendToTarget()
                outputStream.write(buff)
                Log.d(TAG, "保存图片完成")
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                if (reader != null) {
                    reader!!.close()
                }
                if (outputStream != null) {
                    try {
                        outputStream.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private inner class InnerCallBack : Handler.Callback {
        override fun handleMessage(message: Message): Boolean {
            when (message.what) {
                SETIMAGE -> {
                    val bm = message.obj as Bitmap
                    mThumbnail!!.setImageBitmap(bm)
                }
                MOVE_FOCK -> {
                    mPreViewBuidler!!.set(CaptureRequest.SCALER_CROP_REGION, picRect)
                    try {
                        mCameraSession!!.setRepeatingRequest(
                            mPreViewBuidler!!.build(), null,
                            mHandler
                        )
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }
            }
            return false
        }
    }

    companion object {
        private const val TAG = "Camera2Fragment"
        private const val SETIMAGE = 1
        private const val MOVE_FOCK = 2

        /**
         * Max preview width that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_WIDTH = 1920

        /**
         * Max preview height that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_HEIGHT = 1080
    }
}