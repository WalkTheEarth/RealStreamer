package com.irlstream.app.engine

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlin.math.abs

/**
 * Generic UVC webcam support. Any USB Video Class camera works — action cameras in
 * webcam mode present themselves as standard UVC devices. No vendor special-casing.
 *
 * We drive the camera stack directly (USBMonitor.openDevice + UVCCamera) and manage the
 * USB permission flow ourselves so targetSdk 34+ receiver rules are respected.
 */
class UvcController(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onDevices(devices: List<UvcDeviceInfo>)
        fun onOpened(info: Info)
        fun onClosed()
        fun onError(message: String)
    }

    data class Info(
        val label: String,
        val width: Int,
        val height: Int,
        val deviceKey: String,
        val deviceName: String,
    )

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val main = Handler(Looper.getMainLooper())
    private val workerThread = HandlerThread("uvc-camera").apply { start() }
    private val worker = Handler(workerThread.looper)
    private val permissionAction = "${context.packageName}.USB_PERMISSION"

    private var monitor: USBMonitor? = null
    private var camera: UVCCamera? = null
    private var controlBlock: USBMonitor.UsbControlBlock? = null
    private var openedDevice: UsbDevice? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var targetWidth = 1280
    private var targetHeight = 720
    private var targetFps = 30
    private var pendingDevice: UsbDevice? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    refresh()
                    val device = intent.usbDevice() ?: return
                    if (pendingDevice != null && deviceKey(pendingDevice!!) == deviceKey(device)) {
                        requestPermission(device)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.usbDevice()
                    refresh()
                    if (device != null && openedDevice != null && deviceKey(openedDevice!!) == deviceKey(device)) {
                        closeInternal()
                    }
                }
                permissionAction -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null && granted) {
                        val wanted = pendingDevice
                        if (wanted == null || deviceKey(wanted) == deviceKey(device)) {
                            pendingDevice = null
                            worker.post { openBlocking(device) }
                        }
                    } else {
                        pendingDevice = null
                        main.post { listener.onError("USB camera permission denied") }
                    }
                }
            }
        }
    }

    fun start() {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(permissionAction)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        refresh()
    }

    fun stop() {
        closeInternal()
        if (registered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
            }
            registered = false
        }
        try {
            monitor?.destroy()
        } catch (_: Throwable) {
        }
        monitor = null
        workerThread.quitSafely()
    }

    fun refresh() {
        val devices = enumerate()
        main.post { listener.onDevices(devices) }
    }

    fun open(
        deviceKey: String?,
        surfaceTexture: SurfaceTexture,
        targetWidth: Int,
        targetHeight: Int,
        fps: Int,
    ) {
        this.surfaceTexture = surfaceTexture
        this.targetWidth = targetWidth
        this.targetHeight = targetHeight
        this.targetFps = fps

        val devices = usbDevices()
        val device = devices.firstOrNull { deviceKey(it) == deviceKey }
            ?: devices.firstOrNull()
        if (device == null) {
            main.post { listener.onError("No UVC camera connected") }
            return
        }
        pendingDevice = device
        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            return
        }
        pendingDevice = null
        closeExisting()
        worker.post { openBlocking(device) }
    }

    fun close() {
        closeExisting()
    }

    /**
     * Closes the UVC camera and waits for the worker to finish. Call this before
     * releasing the compositor's SurfaceTexture or switching producers (the UVC
     * native thread must stop writing into it first).
     */
    fun closeAndWait(timeoutMs: Long = 1_500) {
        pendingDevice = null
        if (!workerThread.isAlive) return
        val latch = java.util.concurrent.CountDownLatch(1)
        val posted = worker.post {
            try {
                closeBlocking()
            } finally {
                latch.countDown()
            }
        }
        if (!posted) return
        try {
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    // -----------------------------------------------------------------------

    private fun openBlocking(device: UsbDevice) {
        closeBlocking()
        var cam: UVCCamera? = null
        var ctrl: USBMonitor.UsbControlBlock? = null
        try {
            val m = monitor ?: USBMonitor(context, noopConnectListener).also { monitor = it }
            ctrl = m.openDevice(device)
            cam = UVCCamera()
            cam.open(ctrl)
            val supported = cam.supportedSizeList.orEmpty()
            val chosen = chooseSize(supported)
            val st = surfaceTexture ?: throw IllegalStateException("Preview surface unavailable")
            val started = startPreviewWithFallback(cam, st, chosen, supported)
            try {
                cam.setAutoFocus(true)
            } catch (_: Throwable) {
            }
            camera = cam
            controlBlock = ctrl
            openedDevice = device
            val name = deviceName(device)
            val info = Info(
                label = "$name · ${started.width}×${started.height}",
                width = started.width,
                height = started.height,
                deviceKey = deviceKey(device),
                deviceName = name,
            )
            main.post { listener.onOpened(info) }
        } catch (t: Throwable) {
            Log.w(TAG, "UVC open failed", t)
            try {
                cam?.stopPreview()
            } catch (_: Throwable) {
            }
            try {
                cam?.destroy()
            } catch (_: Throwable) {
            }
            try {
                ctrl?.close()
            } catch (_: Throwable) {
            }
            main.post { listener.onError("UVC camera failed: ${t.message ?: "unknown error"}") }
        }
    }

    private fun startPreviewWithFallback(
        camera: UVCCamera,
        st: SurfaceTexture,
        chosen: Size,
        supported: List<Size>,
    ): Size {
        val candidates = buildList {
            add(chosen)
            supported.firstOrNull { it.width == 1280 && it.height == 720 }?.let { add(it) }
            supported.firstOrNull { it.width == 640 && it.height == 480 }?.let { add(it) }
        }
        var lastError: Throwable? = null
        for (size in candidates.distinctBy { Triple(it.width, it.height, it.type) }) {
            val formats = listOf(
                size.type.takeIf { it == UVCCamera.FRAME_FORMAT_MJPEG || it == UVCCamera.FRAME_FORMAT_YUYV },
                UVCCamera.FRAME_FORMAT_MJPEG,
                UVCCamera.FRAME_FORMAT_YUYV,
            ).filterNotNull().distinct()
            for (format in formats) {
                try {
                    st.setDefaultBufferSize(size.width, size.height)
                    camera.setPreviewSize(size.width, size.height, format)
                    camera.setPreviewTexture(st)
                    camera.startPreview()
                    return size
                } catch (t: Throwable) {
                    lastError = t
                    try {
                        camera.stopPreview()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
        throw IllegalStateException("No usable UVC preview format: ${lastError?.message}", lastError)
    }

    private fun closeExisting() {
        worker.post { closeBlocking() }
    }

    private fun closeBlocking() {
        val hadCamera = camera != null || openedDevice != null
        try {
            camera?.stopPreview()
        } catch (_: Throwable) {
        }
        try {
            camera?.destroy()
        } catch (_: Throwable) {
        }
        camera = null
        try {
            controlBlock?.close()
        } catch (_: Throwable) {
        }
        controlBlock = null
        openedDevice = null
        if (hadCamera) main.post { listener.onClosed() }
        main.post { refresh() }
    }

    private fun closeInternal() {
        worker.post { closeBlocking() }
        pendingDevice = null
    }

    private fun requestPermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val intent = Intent(permissionAction).setPackage(context.packageName)
        val pi = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        try {
            usbManager.requestPermission(device, pi)
        } catch (t: Throwable) {
            pendingDevice = null
            main.post { listener.onError("Could not request USB permission: ${t.message}") }
        }
    }

    private fun chooseSize(sizes: List<Size>): Size {
        if (sizes.isEmpty()) {
            return Size(UVCCamera.FRAME_FORMAT_MJPEG, 0, 0, 1920, 1080)
        }
        val targetAspect = maxOf(
            targetWidth.toFloat() / targetHeight,
            targetHeight.toFloat() / targetWidth,
        )
        val targetArea = (targetWidth * targetHeight).toFloat()
        return sizes
            .asSequence()
            .filter { it.width in 160..4096 && it.height in 120..2160 }
            .minByOrNull { size ->
                val aspect = maxOf(
                    size.width.toFloat() / size.height,
                    size.height.toFloat() / size.width,
                )
                val aspectPenalty = abs(aspect - targetAspect) * 100f
                val areaPenalty = abs(size.width * size.height - targetArea) / targetArea * 6f
                val tooSmall = if (size.width < targetWidth && size.height < targetHeight) 1f else 0f
                val formatPenalty = if (size.type == UVCCamera.FRAME_FORMAT_MJPEG) 0f else 0.4f
                aspectPenalty + areaPenalty + tooSmall + formatPenalty
            } ?: sizes.first()
    }

    private fun enumerate(): List<UvcDeviceInfo> =
        usbDevices().map { device ->
            UvcDeviceInfo(
                key = deviceKey(device),
                name = deviceName(device),
                vendorId = device.vendorId,
                productId = device.productId,
            )
        }

    private fun usbDevices(): List<UsbDevice> =
        usbManager.deviceList.values
            .filter { isVideoDevice(it) }
            .sortedBy { it.productName ?: "" }

    private fun isVideoDevice(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_VIDEO) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO) return true
        }
        return false
    }

    private fun deviceKey(device: UsbDevice): String =
        "${device.vendorId}:${device.productId}"

    private fun deviceName(device: UsbDevice): String {
        val product = device.productName?.takeIf { it.isNotBlank() }
        val manufacturer = device.manufacturerName?.takeIf { it.isNotBlank() }
        return product ?: manufacturer ?: "USB camera %04x:%04x".format(device.vendorId, device.productId)
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? = getParcelableExtra(UsbManager.EXTRA_DEVICE)

    private val noopConnectListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice) = Unit
        override fun onDettach(device: UsbDevice) = Unit
        override fun onConnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock, createNew: Boolean) = Unit
        override fun onDisconnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) = Unit
        override fun onCancel(device: UsbDevice) = Unit
    }

    companion object {
        private const val TAG = "UvcController"
    }
}
