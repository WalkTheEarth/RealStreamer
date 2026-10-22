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
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Display
import android.view.Surface
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

    /** A selectable physical or logical camera exposed by Camera2. */
    data class CameraEntry(
        val id: String,
        val label: String,
        val facing: LensFacing,
        val maxWidth: Int,
        val maxHeight: Int,
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

    /**
     * Enumerates every camera the platform exposes, including the extra rear lenses
     * that many phones hide behind a logical camera. Only cameras that can feed a
     * SurfaceTexture are listed.
     */
    fun listCameras(): List<CameraEntry> {
        val ids = try {
            manager.cameraIdList
        } catch (_: Throwable) {
            return emptyList()
        }
        return ids.mapNotNull { id ->
            try {
                val chars = manager.getCameraCharacteristics(id)
                val facingInt = chars.get(CameraCharacteristics.LENS_FACING)
                    ?: CameraCharacteristics.LENS_FACING_BACK
                val facing = if (facingInt == CameraCharacteristics.LENS_FACING_FRONT) {
                    LensFacing.FRONT
                } else {
                    LensFacing.BACK
                }
                val sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(SurfaceTexture::class.java)
                    ?.toList()
                    .orEmpty()
                if (sizes.isEmpty()) return@mapNotNull null
                val largest = sizes.maxByOrNull { it.width.toLong() * it.height }
                    ?: return@mapNotNull null
                val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.minOrNull()
                val focalLabel = focal?.let { String.format(java.util.Locale.US, " · %.1f mm", it) }.orEmpty()
                val facingLabel = if (facing == LensFacing.FRONT) "Front camera" else "Back camera"
                CameraEntry(
                    id = id,
                    label = "$facingLabel$focalLabel",
                    facing = facing,
                    maxWidth = largest.width,
                    maxHeight = largest.height,
                    sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
                )
            } catch (_: Throwable) {
                null
            }
        }.sortedWith(compareBy({ it.facing.ordinal }, { it.id.toIntOrNull() ?: Int.MAX_VALUE }, { it.id }))
    }

    fun open(
        cameraId: String?,
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
                val requestedId = cameraId?.takeIf { candidate ->
                    runCatching { candidate in manager.cameraIdList }.getOrDefault(false)
                }
                val id = requestedId ?: findCameraId(lens) ?: run {
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
                val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                val displayRotation = displayRotationDegrees()
                val mirror = facing == CameraCharacteristics.LENS_FACING_FRONT
                val rotation = relativeRotation(sensorOrientation, displayRotation, mirror)
                val rotated = rotation == 90 || rotation == 270
                // Keep the buffer in the stream's orientation. The compositor applies
                // the sensor rotation; requesting an already-swapped buffer can leave a
                // portrait buffer behind when the display rotation changes at launch.
                val size = chooseSize(sizes, targetWidth, targetHeight)
                val effectiveWidth = if (rotated) size.height else size.width
                val effectiveHeight = if (rotated) size.width else size.height
                Log.i(
                    TAG,
                    "opening camera $id, target=${targetWidth}x$targetHeight -> buffer=${size.width}x${size.height} " +
                        "facing=${if (mirror) "front" else "back"} sensor=$sensorOrientation " +
                        "display=$displayRotation rotation=$rotation mirror=$mirror, sizes=${sizes.size}",
                )
                surfaceTexture.setDefaultBufferSize(size.width, size.height)
                val previewSurface = Surface(surfaceTexture)
                surface = previewSurface

                manager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (closing || generation != openGeneration) {
                            camera.close()
                            return
                        }
                        device = camera
                        Log.i(TAG, "camera $id opened")
                        try {
                            camera.createCaptureSession(listOf(previewSurface), object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(captureSession: CameraCaptureSession) {
                                    if (closing || generation != openGeneration) {
                                        captureSession.close()
                                        return
                                    }
                                    session = captureSession
                                    Log.i(TAG, "capture session configured for $id")
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
                                                label = "${if (facing == CameraCharacteristics.LENS_FACING_FRONT) "Front" else "Back"} camera $id · ${effectiveWidth}×${effectiveHeight}",
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
                        Log.w(TAG, "camera $id disconnected")
                        if (generation != openGeneration) {
                            camera.close()
                            return
                        }
                        isOpen = false
                        camera.close()
                        if (device === camera) device = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.e(TAG, "camera $id error $error")
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
                Log.e(TAG, "CameraAccessException", e)
                onError("Camera access failed: ${e.message}")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException", e)
                onError("Camera permission is required")
            } catch (t: Throwable) {
                Log.e(TAG, "Could not open camera", t)
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
        // CameraManager's order is not guaranteed. Numeric order keeps the default
        // (camera 0) aligned with the first entry shown in the picker.
        val ids = runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList())
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        return ids.firstOrNull { id ->
            try {
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == target
            } catch (_: Throwable) {
                false
            }
        } ?: ids.firstOrNull()
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

    /** Display rotation from the same source used when the camera was opened. */
    fun currentDisplayRotationDegrees(): Int = displayRotationDegrees()

    private fun displayRotationDegrees(): Int {
        return try {
            // Use the physical display rotation, not context.display.rotation. On a
            // fixed-landscape activity the latter can report 0 while the panel is
            // rotated 90 degrees, which makes the camera rotate a landscape buffer into
            // a portrait frame and crop it hard.
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val rotation = dm.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0
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
