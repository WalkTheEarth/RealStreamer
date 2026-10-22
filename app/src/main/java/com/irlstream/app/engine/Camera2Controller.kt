package com.irlstream.app.engine

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import com.irlstream.core.model.LensFacing
import kotlin.math.abs

class Camera2Controller(
    private val context: Context,
    private val onError: (String) -> Unit,
) {
    data class Info(
        val cameraId: String,
        val label: String,
        val width: Int,
        val height: Int,
        val rotationDegrees: Int,
        val mirror: Boolean,
        val facing: Int,
        val sensorOrientation: Int,
    )

    private val manager by lazy { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private val handler = Handler(Looper.getMainLooper())
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var surface: Surface? = null
    private var characteristics: CameraCharacteristics? = null
    private var requestBuilder: CaptureRequest.Builder? = null
    private var torch = false
    private var zoom = 1f
    private var closing = false
    private var openGeneration = 0

    @Volatile
    var isOpen: Boolean = false
        private set

    fun open(
        lens: LensFacing,
        targetWidth: Int,
        targetHeight: Int,
        fps: Int,
        surfaceTexture: SurfaceTexture,
        onOpened: (Info) -> Unit,
    ) {
        close()
        closing = false
        val generation = ++openGeneration
        handler.post {
            try {
                val id = findCameraId(lens) ?: run {
                    onError("No ${if (lens == LensFacing.FRONT) "front" else "back"} camera found")
                    return@post
                }
                val chars = manager.getCameraCharacteristics(id)
                characteristics = chars
                val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: run {
                        onError("Camera configuration unavailable")
                        return@post
                    }
                val sizes = map.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
                if (sizes.isEmpty()) {
                    onError("Camera has no preview output sizes")
                    return@post
                }
                val size = chooseSize(sizes, targetWidth, targetHeight)
                surfaceTexture.setDefaultBufferSize(size.width, size.height)
                val previewSurface = Surface(surfaceTexture)
                surface = previewSurface

                val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                val displayRotation = displayRotationDegrees()
                val mirror = facing == CameraCharacteristics.LENS_FACING_FRONT
                val rotation = relativeRotation(sensorOrientation, displayRotation, mirror)

                manager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (closing || generation != openGeneration) {
                            camera.close()
                            return
                        }
                        device = camera
                        try {
                            camera.createCaptureSession(listOf(previewSurface), object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(captureSession: CameraCaptureSession) {
                                    if (closing || generation != openGeneration) {
                                        captureSession.close()
                                        return
                                    }
                                    session = captureSession
                                    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                        addTarget(previewSurface)
                                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                                        applyFpsRange(this, chars, fps)
                                        applyStabilization(this, chars)
                                        applyZoom(this, chars)
                                        if (torch) set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                                    }
                                    requestBuilder = builder
                                    try {
                                        captureSession.setRepeatingRequest(builder.build(), null, handler)
                                        isOpen = true
                                        onOpened(
                                            Info(
                                                cameraId = id,
                                                label = "${if (facing == CameraCharacteristics.LENS_FACING_FRONT) "Front" else "Back"} camera · ${size.width}×${size.height}",
                                                width = size.width,
                                                height = size.height,
                                                rotationDegrees = rotation,
                                                mirror = mirror,
                                                facing = facing,
                                                sensorOrientation = sensorOrientation,
                                            ),
                                        )
                                    } catch (t: Throwable) {
                                        onError("Camera preview failed: ${t.message}")
                                    }
                                }

                                override fun onConfigureFailed(captureSession: CameraCaptureSession) {
                                    onError("Camera capture session failed")
                                }
                            }, handler)
                        } catch (t: Throwable) {
                            onError("Camera session error: ${t.message}")
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        if (generation != openGeneration) {
                            camera.close()
                            return
                        }
                        isOpen = false
                        camera.close()
                        if (device === camera) device = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        if (generation != openGeneration) {
                            camera.close()
                            return
                        }
                        isOpen = false
                        camera.close()
                        if (device === camera) device = null
                        onError("Camera error ($error)")
                    }
                }, handler)
            } catch (e: CameraAccessException) {
                onError("Camera access failed: ${e.message}")
            } catch (e: SecurityException) {
                onError("Camera permission is required")
            } catch (t: Throwable) {
                onError("Could not open camera: ${t.message}")
            }
        }
    }

    fun close() {
        closing = true
        openGeneration++
        isOpen = false
        try {
            session?.stopRepeating()
        } catch (_: Throwable) {
        }
        try {
            session?.close()
        } catch (_: Throwable) {
        }
        session = null
        try {
            device?.close()
        } catch (_: Throwable) {
        }
        device = null
        try {
            surface?.release()
        } catch (_: Throwable) {
        }
        surface = null
        requestBuilder = null
        characteristics = null
        torch = false
    }

    fun setTorch(enabled: Boolean) {
        torch = enabled
        val builder = requestBuilder ?: return
        val chars = characteristics ?: return
        val flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        if (!flash) return
        builder.set(
            CaptureRequest.FLASH_MODE,
            if (enabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF,
        )
        try {
            session?.setRepeatingRequest(builder.build(), null, handler)
        } catch (_: Throwable) {
        }
    }

    fun setZoom(ratio: Float) {
        zoom = ratio
        val builder = requestBuilder ?: return
        val chars = characteristics ?: return
        applyZoom(builder, chars)
        try {
            session?.setRepeatingRequest(builder.build(), null, handler)
        } catch (_: Throwable) {
        }
    }

    private fun applyZoom(builder: CaptureRequest.Builder, chars: CameraCharacteristics) {
        val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        val sensor = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val clamped = zoom.coerceIn(1f, maxZoom)
        val cropW = (sensor.width() / clamped).toInt().coerceAtLeast(2)
        val cropH = (sensor.height() / clamped).toInt().coerceAtLeast(2)
        val cx = sensor.centerX()
        val cy = sensor.centerY()
        builder.set(
            CaptureRequest.SCALER_CROP_REGION,
            android.graphics.Rect(cx - cropW / 2, cy - cropH / 2, cx + cropW / 2, cy + cropH / 2),
        )
    }

    private fun applyFpsRange(builder: CaptureRequest.Builder, chars: CameraCharacteristics, fps: Int) {
        val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList().orEmpty()
        val chosen = ranges
            .filter { it.upper >= fps }
            .minByOrNull { (it.upper - fps) * 10 + (fps - it.lower).coerceAtLeast(0) }
            ?: ranges.minByOrNull { it.upper } ?: return
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(chosen.lower, chosen.upper))
    }

    private fun applyStabilization(builder: CaptureRequest.Builder, chars: CameraCharacteristics) {
        val eis = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            ?.contains(CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true
        if (eis) {
            builder.set(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON,
            )
        }
    }

    private fun findCameraId(lens: LensFacing): String? {
        val target = if (lens == LensFacing.FRONT) CameraCharacteristics.LENS_FACING_FRONT
        else CameraCharacteristics.LENS_FACING_BACK
        return manager.cameraIdList.firstOrNull { id ->
            try {
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == target
            } catch (_: Throwable) {
                false
            }
        } ?: manager.cameraIdList.firstOrNull()
    }

    private fun chooseSize(sizes: List<Size>, targetWidth: Int, targetHeight: Int): Size {
        val targetAspect = maxOf(
            targetWidth.toFloat() / targetHeight.toFloat(),
            targetHeight.toFloat() / targetWidth.toFloat(),
        )
        val targetArea = (targetWidth * targetHeight).toFloat()
        return sizes
            .asSequence()
            .filter { it.width <= 3840 && it.height <= 2160 }
            .minByOrNull { size ->
                val aspect = maxOf(
                    size.width.toFloat() / size.height.toFloat(),
                    size.height.toFloat() / size.width.toFloat(),
                )
                val aspectPenalty = abs(aspect - targetAspect) * 100f
                val areaPenalty = abs(size.width * size.height - targetArea) / targetArea * 6f
                val tooSmall = if (size.width < targetWidth && size.height < targetHeight) 1.5f else 0f
                aspectPenalty + areaPenalty + tooSmall
            } ?: sizes.first()
    }

    private fun displayRotationDegrees(): Int {
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            val rotation = if (android.os.Build.VERSION.SDK_INT >= 30) {
                context.display?.rotation ?: wm.defaultDisplay.rotation
            } else {
                wm.defaultDisplay.rotation
            }
            rotation * 90
        } catch (_: Throwable) {
            0
        }
    }

    companion object {
        private const val TAG = "Camera2Controller"

        /**
         * Angle to rotate the sensor buffer so the image is upright on screen, for use in
         * a GL shader. Front cameras use the mirrored (outgoing) direction.
         */
        fun relativeRotation(sensorOrientation: Int, displayRotationDegrees: Int, front: Boolean): Int {
            val raw = if (front) {
                sensorOrientation + displayRotationDegrees
            } else {
                sensorOrientation - displayRotationDegrees
            }
            return ((raw % 360) + 360) % 360
        }
    }
}
