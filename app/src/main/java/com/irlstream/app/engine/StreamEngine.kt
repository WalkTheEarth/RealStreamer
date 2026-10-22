package com.irlstream.app.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import com.irlstream.app.chat.ChatManager
import com.irlstream.app.data.SettingsRepository
import com.irlstream.core.model.AppSettings
import com.irlstream.core.model.AudioSettings
import com.irlstream.core.model.CameraKind
import com.irlstream.core.model.LensFacing
import com.irlstream.core.model.SourceConfig
import com.irlstream.core.model.SourceType
import com.irlstream.core.model.VideoSettings
import com.irlstream.core.rtmp.RtmpUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class StreamEngine private constructor(
    private val context: Context,
    private val repository: SettingsRepository,
) {
    companion object {
        private const val TAG = "StreamEngine"

        @SuppressLint("StaticFieldLeak") // application context only
        @Volatile
        private var instance: StreamEngine? = null

        fun initialize(context: Context, repository: SettingsRepository) {
            if (instance != null) return
            synchronized(this) {
                if (instance == null) {
                    instance = StreamEngine(context.applicationContext, repository).also { it.start() }
                }
            }
        }

        fun get(): StreamEngine = instance ?: error("StreamEngine not initialized")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var settings: AppSettings = repository.settings.value

    private val _state = MutableStateFlow(
        EngineState(
            resolution = settings.video.display,
            audioMuted = settings.audio.muted,
        ),
    )
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private var started = false
    private var settingsJob: Job? = null

    private var compositor: VideoCompositor? = null
    private var compositorWidth = 0
    private var compositorHeight = 0
    private var compositorFps = 0

    private var camera2: Camera2Controller? = null
    private var uvc: UvcController? = null
    private var chatManager: ChatManager? = null
    private var webManager: WebOverlayManager? = null

    @Volatile private var videoEncoder: VideoEncoder? = null
    @Volatile private var audioEngine: AudioEngine? = null
    @Volatile private var pipeline: StreamPipeline? = null

    private var previewSurface: Surface? = null
    private var encoderSurface: Surface? = null
    private var previewStarted = false
    private var cameraGeneration = 0
    private var cameraOpening = false
    private var currentCameraKind: CameraKind? = null
    private var connectedUvc: UvcController.Info? = null
    private var builtInCamera: Camera2Controller.Info? = null
    private val displayManager by lazy { context.getSystemService(DisplayManager::class.java) }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) updateBuiltInRotation()
        }
    }
    private var forceBuiltInOnce = false
    private var uvcAttempted = false

    private var liveStartNanos = 0L
    private var liveStartElapsed = 0L
    private var liveVideo: VideoSettings? = null
    private var liveAudio: AudioSettings? = null

    private val framesThisTick = java.util.concurrent.atomic.AtomicLong()
    private val bytesThisTick = java.util.concurrent.atomic.AtomicLong()

    private val chatMessages = ArrayList<ChatMessage>()
    private val overlayImages = HashMap<String, Bitmap>()
    private val webFallbacks = HashMap<String, Bitmap>()
    private val imageKeys = HashMap<String, String>()

    private var lastCameraFrameCount = -1L
    private var lastCameraFrameSample = 0L
    private var lastCameraFrameChangeAt = 0L
    private var cameraNoFrameReported = false
    private var lastContentRefresh = 0L
    private var contentRefreshPending = false
    private var serviceStarted = false
    private var webSyncJob: Job? = null

    private fun start() {
        if (started) return
        started = true

        camera2 = Camera2Controller(context) { message -> onCameraError(message) }
        refreshCameras()
        uvc = UvcController(context, uvcListener).also { it.start() }
        try {
            displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        } catch (_: Throwable) {
        }
        chatManager = ChatManager(scope, ::onChatMessage, ::onChatStatus)

        settingsJob = scope.launch {
            repository.settings.collect { newSettings -> applySettings(newSettings) }
        }
        scope.launch { tickLoop() }
        rebuildIdleDestinations()
    }

    // -----------------------------------------------------------------------
    // Settings
    // -----------------------------------------------------------------------

    private fun applySettings(newSettings: AppSettings) {
        val old = settings
        if (newSettings == old) return
        settings = newSettings

        _state.update { st ->
            st.copy(
                audioMuted = newSettings.audio.muted,
                resolution = if (st.isLive) st.resolution else newSettings.video.display,
            )
        }

        if (old.scene != newSettings.scene) {
            compositor?.setScene(newSettings.scene)
            scheduleWebSync()
            loadImages()
            refreshContent()
        }

        val cameraSourceChanged = old.camera.kind != newSettings.camera.kind ||
            old.camera.lens != newSettings.camera.lens ||
            old.camera.cameraId != newSettings.camera.cameraId ||
            old.camera.uvcDeviceKey != newSettings.camera.uvcDeviceKey ||
            old.camera.fallbackToBuiltIn != newSettings.camera.fallbackToBuiltIn
        val cameraTransformChanged = old.camera.rotationOffsetDegrees != newSettings.camera.rotationOffsetDegrees ||
            old.camera.mirrorImage != newSettings.camera.mirrorImage ||
            old.camera.mirrorFrontCamera != newSettings.camera.mirrorFrontCamera ||
            old.camera.fitCamera != newSettings.camera.fitCamera
        if (cameraSourceChanged) {
            uvcAttempted = false
            if (previewStarted) openCamera()
        } else if (cameraTransformChanged) {
            // Rotation/mirror changes must not restart the sensor; update the compositor
            // transform in place so the preview and encoder turn immediately.
            updateCameraTransform()
        }

        if (old.video != newSettings.video) {
            if (_state.value.isLive || _state.value.isBusy) {
                _state.update { it.copy(pendingVideoSettings = true) }
            } else if (previewStarted || previewSurface != null) {
                restartPreviewPipeline()
            }
        }

        if (old.audio != newSettings.audio && _state.value.isLive && old.audio.copy(muted = false) != newSettings.audio.copy(muted = false)) {
            _state.update { it.copy(pendingAudioSettings = true) }
        }

        if (old.chat != newSettings.chat) {
            chatManager?.update(newSettings.chat)
        }

        if (old.destinations != newSettings.destinations) {
            if (_state.value.isLive) {
                pipeline?.updateDestinations(newSettings.activeDestinations)
                val activeIds = newSettings.destinations.map { it.id }.toSet()
                _state.update { st ->
                    st.copy(
                        destinations = st.destinations.filter { it.id in activeIds },
                    )
                }
            } else {
                rebuildIdleDestinations()
            }
        }
    }

    private fun rebuildIdleDestinations() {
        val items = settings.destinations.map { destination ->
            DestinationRuntime(
                id = destination.id,
                name = destination.displayName,
                maskedUrl = RtmpUrl.parse(destination.url)?.masked() ?: "—",
                status = if (destination.enabled) DestinationStatus.OFFLINE else DestinationStatus.DISABLED,
            )
        }
        _state.update { it.copy(destinations = items) }
    }

    // -----------------------------------------------------------------------
    // Preview lifecycle
    // -----------------------------------------------------------------------

    /**
     * Attaches a preview Surface owned by a SurfaceView. The engine never releases this
     * Surface; the surface holder does.
     */
    fun attachPreview(surface: Surface) {
        if (previewSurface === surface && compositor != null) return
        detachPreviewInternal(keepCompositor = true)
        previewSurface = surface
        ensureCompositor()
        compositor?.setPreviewSurface(surface)
        _state.update { it.copy(previewAttached = true) }
        startPreviewIfReady()
    }

    /**
     * Some devices need the EGL window surface recreated when the SurfaceView's
     * buffer geometry changes.
     */
    fun onPreviewSurfaceChanged(surface: Surface) {
        if (previewSurface !== surface || compositor == null) return
        compositor?.setPreviewSurface(null)
        compositor?.setPreviewSurface(surface)
    }

    fun detachPreview(surface: Surface? = null) {
        if (surface != null && previewSurface !== surface) return
        compositor?.setPreviewSurface(null)
        previewSurface = null
        _state.update { it.copy(previewAttached = false) }
        if (!_state.value.isLive) {
            pausePreviewInternal()
        }
    }

    private fun detachPreviewInternal(keepCompositor: Boolean) {
        compositor?.setPreviewSurface(null)
        previewSurface = null
        _state.update { it.copy(previewAttached = false) }
        if (!keepCompositor) {
            pausePreviewInternal()
        }
    }

    /** UI calls this once camera permission is available. */
    fun startPreview() {
        if (previewSurface == null) return
        ensureCompositor()
        startPreviewIfReady()
    }

    /** Called by the host when the app goes to the background. */
    fun onHostPause() {
        if (_state.value.isLive) return
        // Keep the camera closed in the background without a foreground service.
        pausePreviewInternal()
    }

    private fun pausePreviewInternal() {
        if (_state.value.isLive) return
        previewStarted = false
        cameraGeneration++
        cameraOpening = false
        camera2?.close()
        uvc?.closeAndWait()
        chatManager?.stop()
        webManager?.release()
        webManager = null
        compositor?.release()
        compositor = null
        compositorWidth = 0
        compositorHeight = 0
        compositorFps = 0
        previewSurface = null
        encoderSurface = null
        _state.update { it.copy(outputWidth = 0, outputHeight = 0) }
        currentCameraKind = null
        connectedUvc = null
        _state.update {
            it.copy(
                phase = StreamPhase.IDLE,
                previewAttached = false,
                cameraLabel = "Camera idle",
                cameraKind = null,
                audioLevel = 0f,
            )
        }
    }

    private fun startPreviewIfReady() {
        if (previewSurface == null) return
        if (!hasPermission(Manifest.permission.CAMERA)) return
        if (!previewStarted) {
            previewStarted = true
            chatManager?.start(settings.chat)
        }
        if (currentCameraKind == null && !cameraOpening) {
            openCamera()
        }
        _state.update {
            if (it.phase == StreamPhase.IDLE) it.copy(phase = StreamPhase.PREVIEW) else it
        }
    }

    private fun activeVideo(): VideoSettings = liveVideo ?: settings.video

    private fun ensureCompositor() {
        val video = activeVideo()
        if (compositor != null &&
            compositorWidth == video.width &&
            compositorHeight == video.height &&
            compositorFps == video.fps
        ) {
            encoderSurface?.let { compositor?.setEncoderSurface(it) }
            return
        }
        releaseCompositor()
        val c = VideoCompositor(context, video.width, video.height, video.fps, compositorListener)
        compositor = c
        compositorWidth = video.width
        compositorHeight = video.height
        compositorFps = video.fps
        _state.update { it.copy(outputWidth = video.width, outputHeight = video.height) }
        c.start()
        c.setScene(settings.scene)
        c.setContent(buildContent())
        if (_state.value.isLive && liveStartNanos > 0) c.setStreamEpoch(liveStartNanos)
        previewSurface?.let { c.setPreviewSurface(it) }
        if (_state.value.isLive) encoderSurface?.let { c.setEncoderSurface(it) }
        syncWebSources()
        loadImages()
        refreshContent()
    }

    private fun releaseCompositor() {
        webManager?.release()
        webManager = null
        compositor?.release()
        compositor = null
        compositorWidth = 0
        compositorHeight = 0
        compositorFps = 0
        webFallbacks.clear()
        _state.update { it.copy(outputWidth = 0, outputHeight = 0) }
    }

    private fun restartPreviewPipeline() {
        val wasStarted = previewStarted
        cameraGeneration++
        cameraOpening = false
        camera2?.close()
        uvc?.closeAndWait()
        currentCameraKind = null
        connectedUvc = null
        releaseCompositor()
        ensureCompositor()
        if (wasStarted) {
            previewStarted = true
            openCamera()
        }
    }

    // -----------------------------------------------------------------------
    // Camera
    // -----------------------------------------------------------------------

    private fun openCamera() {
        val c = compositor ?: run {
            Log.w(TAG, "openCamera: no compositor")
            return
        }
        if (!previewStarted) {
            Log.w(TAG, "openCamera: preview not started")
            return
        }
        // While live, the camera can be switched even if the preview Surface is not
        // attached (for example from the Settings screen); the encoder keeps receiving
        // frames from the compositor.
        if (previewSurface == null && !_state.value.isLive) {
            Log.w(TAG, "openCamera: no preview surface")
            return
        }
        if (!hasPermission(Manifest.permission.CAMERA)) {
            Log.w(TAG, "openCamera: camera permission missing")
            postError("Camera permission is required")
            return
        }
        val gen = ++cameraGeneration
        lastCameraFrameCount = -1L
        lastCameraFrameChangeAt = SystemClock.elapsedRealtime()
        cameraNoFrameReported = false
        Log.i(TAG, "openCamera gen=$gen kind=${settings.camera.kind} lens=${settings.camera.lens} devices=${_state.value.uvcDevices.size} surface=$previewSurface")
        cameraOpening = true
        camera2?.close()
        uvc?.closeAndWait()
        currentCameraKind = null
        connectedUvc = null
        builtInCamera = null

        val forcedBuiltIn = forceBuiltInOnce
        forceBuiltInOnce = false
        val kind = if (forcedBuiltIn) CameraKind.BUILT_IN else settings.camera.kind
        val devices = _state.value.uvcDevices
        val useUvc = kind == CameraKind.USB_UVC && devices.isNotEmpty()

        if (kind == CameraKind.USB_UVC && !useUvc) {
            if (settings.camera.fallbackToBuiltIn) {
                postError("UVC camera not connected — using the built-in camera")
            } else {
                cameraOpening = false
                postError("UVC camera not connected")
                _state.update { it.copy(cameraLabel = "UVC camera offline", cameraKind = CameraKind.USB_UVC) }
                return
            }
        }

        val video = activeVideo()
        if (useUvc) {
            uvcAttempted = true
            _state.update { it.copy(cameraLabel = "Opening UVC camera…", cameraKind = CameraKind.USB_UVC) }
            c.provideCameraTexture(
                video.width,
                video.height,
                0,
                false,
            ) { st ->
                if (gen != cameraGeneration) return@provideCameraTexture
                uvc?.open(
                    deviceKey = settings.camera.uvcDeviceKey,
                    surfaceTexture = st,
                    targetWidth = video.width,
                    targetHeight = video.height,
                    fps = video.fps,
                )
            }
        } else {
            _state.update { it.copy(cameraLabel = "Opening camera…") }
            val selectedFacing = _state.value.builtInCameras
                .firstOrNull { it.id == settings.camera.cameraId }
                ?.facing
                ?: settings.camera.lens
            val initialMirror = (selectedFacing == LensFacing.FRONT && settings.camera.mirrorFrontCamera) xor
                settings.camera.mirrorImage
            c.provideCameraTexture(
                video.width,
                video.height,
                settings.camera.rotationOffsetDegrees,
                initialMirror,
            ) { st ->
                Log.i(TAG, "camera texture ready gen=$gen st=$st")
                if (gen != cameraGeneration) return@provideCameraTexture
                camera2?.open(
                    cameraId = settings.camera.cameraId,
                    lens = settings.camera.lens,
                    targetWidth = video.width,
                    targetHeight = video.height,
                    fps = video.fps,
                    surfaceTexture = st,
                ) { info ->
                    if (gen != cameraGeneration) return@open
                    cameraOpening = false
                    currentCameraKind = CameraKind.BUILT_IN
                    connectedUvc = null
                    builtInCamera = info
                    applyBuiltInTransform(info)
                    _state.update {
                        it.copy(
                            cameraLabel = info.label,
                            cameraKind = CameraKind.BUILT_IN,
                            uvcAttached = _state.value.uvcDevices.isNotEmpty(),
                        )
                    }
                }
            }
        }
    }

    private fun onCameraError(message: String) {
        cameraOpening = false
        postError(message)
    }

    /** Re-enumerate the cameras exposed by Camera2 (cheap; safe during preview). */
    fun refreshCameras() {
        val cameras = camera2?.listCameras().orEmpty().map { entry ->
            BuiltInCameraInfo(
                id = entry.id,
                label = entry.label,
                facing = entry.facing,
                maxWidth = entry.maxWidth,
                maxHeight = entry.maxHeight,
            )
        }
        if (cameras.isNotEmpty()) {
            Log.i(TAG, "built-in cameras: " + cameras.joinToString { "${it.id}=${it.label}" })
        }
        _state.update { it.copy(builtInCameras = cameras) }
    }

    fun switchCamera() {
        val cameras = _state.value.builtInCameras
        if (settings.camera.kind == CameraKind.USB_UVC) {
            val target = cameras.firstOrNull { it.facing == settings.camera.lens } ?: cameras.firstOrNull()
            repository.updateCamera {
                it.copy(
                    kind = CameraKind.BUILT_IN,
                    lens = target?.facing ?: it.lens,
                    cameraId = target?.id ?: it.cameraId,
                )
            }
            return
        }
        val currentFacing = builtInCamera?.let {
            if (it.facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
                LensFacing.FRONT
            } else {
                LensFacing.BACK
            }
        } ?: settings.camera.lens
        val currentId = builtInCamera?.cameraId ?: settings.camera.cameraId
        val target = cameras.firstOrNull { it.facing != currentFacing && it.id != currentId }
            ?: cameras.firstOrNull { it.id != currentId }
        if (target != null) {
            repository.updateCamera {
                it.copy(kind = CameraKind.BUILT_IN, lens = target.facing, cameraId = target.id)
            }
        } else {
            repository.updateCamera {
                it.copy(
                    kind = CameraKind.BUILT_IN,
                    lens = if (currentFacing == LensFacing.BACK) LensFacing.FRONT else LensFacing.BACK,
                    cameraId = null,
                )
            }
        }
    }

    fun selectCamera(
        kind: CameraKind,
        lens: LensFacing = settings.camera.lens,
        uvcKey: String? = settings.camera.uvcDeviceKey,
        cameraId: String? = settings.camera.cameraId,
    ) {
        repository.updateCamera {
            it.copy(kind = kind, lens = lens, uvcDeviceKey = uvcKey, cameraId = cameraId)
        }
    }

    fun setTorch(enabled: Boolean) {
        camera2?.setTorch(enabled)
    }

    fun setZoom(ratio: Float) {
        camera2?.setZoom(ratio)
    }

    fun refreshUvc() {
        uvcAttempted = false
        uvc?.refresh()
        if (previewStarted && settings.camera.kind == CameraKind.USB_UVC && currentCameraKind != CameraKind.USB_UVC) {
            openCamera()
        }
    }

    private fun updateBuiltInRotation() {
        if (currentCameraKind != CameraKind.BUILT_IN) return
        val info = builtInCamera ?: return
        applyBuiltInTransform(info)
    }

    private fun applyBuiltInTransform(info: Camera2Controller.Info) {
        // Use the same display source as Camera2Controller.open. DisplayManager can
        // report a different rotation than the activity's display while the app is
        // locked to landscape, which previously overwrote a correct camera transform
        // with a sideways one a frame after the camera opened.
        val rotation = camera2?.currentDisplayRotationDegrees() ?: 0
        val front = info.facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
        val relative = Camera2Controller.relativeRotation(info.sensorOrientation, rotation, front)
        compositor?.setCameraSourceInfo(
            info.width,
            info.height,
            effectiveCameraRotation(relative),
            effectiveCameraMirror(info.mirror),
            settings.camera.fitCamera,
        )
    }

    private fun updateCameraTransform() {
        when (currentCameraKind) {
            CameraKind.BUILT_IN -> updateBuiltInRotation()
            CameraKind.USB_UVC -> connectedUvc?.let { info ->
                val active = activeVideo()
                val base = if ((active.height > active.width) != (info.height > info.width)) 90 else 0
                compositor?.setCameraSourceInfo(
                    info.width,
                    info.height,
                    effectiveCameraRotation(base),
                    effectiveCameraMirror(false),
                    settings.camera.fitCamera,
                )
            }
            else -> Unit
        }
    }

    private fun effectiveCameraRotation(autoRotation: Int): Int {
        val total = autoRotation + settings.camera.rotationOffsetDegrees
        return ((total % 360) + 360) % 360
    }

    private fun effectiveCameraMirror(autoMirror: Boolean): Boolean {
        // The sensor is mirrored for front cameras. mirrorFrontCamera is the default
        // selfie flip; mirrorImage is an explicit manual flip that also covers UVC.
        val frontDefault = autoMirror && settings.camera.mirrorFrontCamera
        return frontDefault xor settings.camera.mirrorImage
    }

    // -----------------------------------------------------------------------
    // UVC
    // -----------------------------------------------------------------------

    private val uvcListener = object : UvcController.Listener {
        override fun onDevices(devices: List<UvcDeviceInfo>) {
            val previous = _state.value.uvcDevices
            _state.update { it.copy(uvcDevices = devices, uvcAttached = devices.isNotEmpty()) }
            if (devices.isEmpty() || previous.isEmpty()) {
                // New hardware appeared or all hardware left: allow one fresh auto-open.
                uvcAttempted = false
            }
            if (settings.camera.kind == CameraKind.USB_UVC &&
                devices.isNotEmpty() &&
                previewStarted &&
                currentCameraKind != CameraKind.USB_UVC &&
                !cameraOpening &&
                !uvcAttempted
            ) {
                uvcAttempted = true
                openCamera()
            }
        }

        override fun onOpened(info: UvcController.Info) {
            cameraOpening = false
            if (!previewStarted || settings.camera.kind != CameraKind.USB_UVC) {
                uvc?.close()
                return
            }
            uvcAttempted = true
            currentCameraKind = CameraKind.USB_UVC
            connectedUvc = info
            builtInCamera = null
            updateCameraTransform()
            _state.update {
                it.copy(
                    cameraLabel = info.label,
                    cameraKind = CameraKind.USB_UVC,
                    uvcAttached = true,
                )
            }
        }

        override fun onClosed() {
            if (currentCameraKind != CameraKind.USB_UVC) return
            currentCameraKind = null
            connectedUvc = null
            _state.update { it.copy(cameraLabel = "UVC camera disconnected") }
            if (previewStarted && settings.camera.kind == CameraKind.USB_UVC) {
                if (settings.camera.fallbackToBuiltIn) {
                    forceBuiltInOnce = true
                    openCamera()
                } else {
                    postError("UVC camera disconnected")
                }
            }
        }

        override fun onError(message: String) {
            cameraOpening = false
            postError(message)
            if (previewStarted &&
                settings.camera.kind == CameraKind.USB_UVC &&
                currentCameraKind != CameraKind.USB_UVC &&
                settings.camera.fallbackToBuiltIn
            ) {
                forceBuiltInOnce = true
                openCamera()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Live
    // -----------------------------------------------------------------------

    fun goLive() {
        if (_state.value.isLive || _state.value.isBusy) return
        val enabled = settings.destinations.filter { it.enabled }
        if (enabled.isEmpty()) {
            postError("Add and enable at least one RTMP destination")
            return
        }
        if (enabled.none { RtmpUrl.isValid(it.url) }) {
            postError("None of the enabled destinations has a valid RTMP URL")
            return
        }
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            postError("Microphone permission is required to go live")
            return
        }
        if (currentCameraKind == null) {
            postError("Camera is not ready yet")
            return
        }
        val video = settings.video
        if (compositorWidth != video.width || compositorHeight != video.height || compositorFps != video.fps) {
            postError("Applying video settings — try going live again in a moment")
            restartPreviewPipeline()
            return
        }
        val c = compositor ?: run {
            postError("Preview is not ready")
            return
        }

        try {
            val audio = settings.audio
            liveVideo = video
            liveAudio = audio
            liveStartNanos = SystemClock.elapsedRealtimeNanos()
            liveStartElapsed = SystemClock.elapsedRealtime()
            framesThisTick.set(0)
            bytesThisTick.set(0)
            c.setStreamEpoch(liveStartNanos)

            val streamPipeline = StreamPipeline(::onDestinationState, ::requestKeyFrame)
            streamPipeline.start(enabled)
            pipeline = streamPipeline

            val encoder = VideoEncoder(
                width = video.width,
                height = video.height,
                fps = video.fps,
                bitrateKbps = video.bitrateKbps,
                keyframeIntervalSec = video.keyframeIntervalSec,
                listener = videoListener,
            )
            videoEncoder = encoder
            val inputSurface = encoder.start()
            encoderSurface = inputSurface
            c.setEncoderSurface(inputSurface)

            val audioEngine = AudioEngine(context, audio, liveStartNanos, audioListener)
            audioEngine.start()
            this.audioEngine = audioEngine

            _state.update {
                it.copy(
                    phase = StreamPhase.LIVE,
                    liveStartedAtMs = System.currentTimeMillis(),
                    durationMs = 0,
                    resolution = video.display,
                    fps = 0f,
                    videoBitrateKbps = 0,
                    pendingVideoSettings = false,
                    pendingAudioSettings = false,
                    audioMuted = audio.muted,
                )
            }
            encoder.requestKeyFrame()
            startStreamingService()
        } catch (t: Throwable) {
            Log.e(TAG, "goLive failed", t)
            postError("Could not start streaming: ${t.message}")
            try {
                stopLiveInternal()
            } catch (_: Throwable) {
            }
            _state.update {
                it.copy(
                    phase = if (previewStarted) StreamPhase.PREVIEW else StreamPhase.IDLE,
                    liveStartedAtMs = null,
                    durationMs = 0,
                    destinations = idleDestinationStates(),
                    fps = 0f,
                    videoBitrateKbps = 0,
                )
            }
        }
    }

    fun stopLive() {
        if (!_state.value.isLive && !_state.value.isBusy) return
        _state.update { it.copy(phase = StreamPhase.STOPPING) }
        // Detach the encoder surface first so the compositor stops producing frames
        // immediately, then tear the network/encoders down off the main thread.
        compositor?.setEncoderSurface(null)
        scope.launch(Dispatchers.Default) {
            try {
                stopLiveInternal()
            } catch (t: Throwable) {
                Log.w(TAG, "live teardown failed", t)
            }
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        phase = if (previewStarted) StreamPhase.PREVIEW else StreamPhase.IDLE,
                        liveStartedAtMs = null,
                        durationMs = 0,
                        fps = 0f,
                        videoBitrateKbps = 0,
                        droppedFrames = 0,
                        audioLevel = 0f,
                        pendingVideoSettings = false,
                        pendingAudioSettings = false,
                        destinations = idleDestinationStates(),
                    )
                }
                if (!_state.value.previewAttached) {
                    // Stopped from the notification or another screen: release the
                    // camera/mic/compositor instead of leaving capture running with no
                    // preview and no foreground service.
                    pausePreviewInternal()
                } else if (previewStarted) {
                    val v = settings.video
                    if (compositorWidth != v.width || compositorHeight != v.height || compositorFps != v.fps) {
                        restartPreviewPipeline()
                    }
                }
            }
        }
    }

    private fun stopLiveInternal() {
        pipeline?.stop()
        pipeline = null
        audioEngine?.stop()
        audioEngine = null
        compositor?.setEncoderSurface(null)
        encoderSurface = null
        videoEncoder?.stop()
        videoEncoder = null
        liveVideo = null
        liveAudio = null
        liveStartNanos = 0
        liveStartElapsed = 0
        framesThisTick.set(0)
        bytesThisTick.set(0)
        stopStreamingService()
    }

    private fun idleDestinationStates(): List<DestinationRuntime> = settings.destinations.map { d ->
        DestinationRuntime(
            id = d.id,
            name = d.displayName,
            maskedUrl = RtmpUrl.parse(d.url)?.masked() ?: "—",
            status = if (d.enabled) DestinationStatus.OFFLINE else DestinationStatus.DISABLED,
        )
    }

    private fun requestKeyFrame() {
        videoEncoder?.requestKeyFrame()
    }

    private val videoListener = object : VideoEncoder.Listener {
        override fun onConfig(avcC: ByteArray) {
            pipeline?.setVideoConfig(avcC)
        }

        override fun onFrame(data: ByteArray, ptsMs: Long, keyframe: Boolean) {
            framesThisTick.incrementAndGet()
            bytesThisTick.addAndGet(data.size + 24L)
            pipeline?.dispatchVideo(EncodedVideoFrame(data, ptsMs, keyframe))
        }

        override fun onError(message: String) {
            postError(message)
            if (_state.value.isLive) {
                scope.launch {
                    stopLive()
                }
            }
        }
    }

    private val audioListener = object : AudioEngine.Listener {
        override fun onConfig(asc: ByteArray) {
            pipeline?.setAudioConfig(asc)
        }

        override fun onFrame(rawAac: ByteArray, ptsUs: Long) {
            if (settings.audio.muted) return
            pipeline?.dispatchAudio(EncodedAudioFrame(rawAac, ptsUs / 1000))
        }

        override fun onLevel(level: Float) {
            _state.update { it.copy(audioLevel = level) }
        }

        override fun onError(message: String) {
            postError(message)
        }
    }

    private fun onDestinationState(runtime: DestinationRuntime) {
        _state.update { st ->
            val configured = settings.destinations.firstOrNull { it.id == runtime.id }
            if (configured == null) {
                return@update st.copy(destinations = st.destinations.filterNot { it.id == runtime.id })
            }
            val effective = if (!configured.enabled) {
                DestinationRuntime(
                    id = configured.id,
                    name = configured.displayName,
                    maskedUrl = RtmpUrl.parse(configured.url)?.masked() ?: "—",
                    status = DestinationStatus.DISABLED,
                )
            } else {
                runtime
            }
            val list = st.destinations.toMutableList()
            val index = list.indexOfFirst { it.id == effective.id }
            if (index >= 0) {
                val old = list[index]
                list[index] = effective.copy(
                    name = effective.name.ifBlank { old.name },
                    maskedUrl = effective.maskedUrl.ifBlank { old.maskedUrl },
                )
            } else {
                list.add(effective)
            }
            st.copy(destinations = list)
        }
    }

    // -----------------------------------------------------------------------
    // Chat
    // -----------------------------------------------------------------------

    private fun onChatMessage(message: ChatMessage) {
        scope.launch {
            synchronized(chatMessages) {
                chatMessages.add(message)
                while (chatMessages.size > 300) chatMessages.removeAt(0)
            }
            _state.update { it.copy(chat = synchronized(chatMessages) { chatMessages.toList() }) }
            scheduleContentRefresh()
        }
    }

    private fun onChatStatus(status: String) {
        _state.update { it.copy(chatStatus = status) }
    }

    // -----------------------------------------------------------------------
    // Overlay content
    // -----------------------------------------------------------------------

    private fun buildContent(): OverlayContent {
        val st = _state.value
        val duration = if (st.isLive) st.durationMs else 0
        val tokens = mapOf(
            "time" to java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date()),
            "uptime" to OverlayRenderer.formatDuration(duration),
            "fps" to String.format(Locale.US, "%.1f", st.fps),
            "bitrate" to String.format(Locale.US, "%.1f Mbps", st.videoBitrateKbps / 1000f),
            "resolution" to (liveVideo?.display ?: settings.video.display),
            "destinations" to "${st.destinations.count { it.status == DestinationStatus.LIVE }}/${st.destinations.count { it.status != DestinationStatus.DISABLED }}",
        )
        val images = HashMap<String, Bitmap>()
        images.putAll(overlayImages)
        images.putAll(webFallbacks)
        val overlayChat = synchronized(chatMessages) {
            if (settings.chat.hideCommands) {
                chatMessages.filterNot { it.text.trimStart().startsWith("!") }
            } else {
                chatMessages.toList()
            }
        }
        return OverlayContent(
            chat = overlayChat,
            stats = st.statsSnapshot(),
            tokens = tokens,
            images = images,
        )
    }

    private fun refreshContent() {
        compositor?.setContent(buildContent())
    }

    private fun scheduleContentRefresh() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastContentRefresh >= 220) {
            lastContentRefresh = now
            refreshContent()
        } else if (!contentRefreshPending) {
            contentRefreshPending = true
            scope.launch {
                delay(240)
                contentRefreshPending = false
                lastContentRefresh = SystemClock.elapsedRealtime()
                refreshContent()
            }
        }
    }

    private fun loadImages() {
        val enabledImages = settings.scene.broadcastSources
            .filter { it.type == SourceType.IMAGE && it.enabled }
            .mapNotNull { source ->
                val uri = (source.config as? SourceConfig.Image)?.uri?.takeIf { it.isNotBlank() }
                if (uri != null) source.id to uri else null
            }
            .toMap()

        imageKeys.keys.filter { it !in enabledImages.keys }.forEach { id ->
            imageKeys.remove(id)
            overlayImages.remove(id)
        }
        enabledImages.forEach { (id, uri) ->
            if (imageKeys[id] == uri && overlayImages.containsKey(id)) return@forEach
            imageKeys[id] = uri
            scope.launch(Dispatchers.IO) {
                val bitmap = decodeBitmap(uri)
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        overlayImages[id] = bitmap
                        scheduleContentRefresh()
                    } else {
                        imageKeys.remove(id)
                        postError("Could not load the selected image")
                    }
                }
            }
        }
    }

    private fun decodeBitmap(uriString: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openImageStream(uriString)?.use { BitmapFactory.decodeStream(it, null, options) }
            var sample = 1
            val max = maxOf(options.outWidth, options.outHeight)
            while (max / sample > 2048) sample *= 2
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            openImageStream(uriString)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        } catch (t: Throwable) {
            Log.w(TAG, "image decode failed", t)
            null
        }
    }

    private fun openImageStream(uriString: String): java.io.InputStream? {
        return try {
            when {
                uriString.startsWith("content://") || uriString.startsWith("file://") ->
                    context.contentResolver.openInputStream(android.net.Uri.parse(uriString))
                else -> File(uriString).inputStream()
            }
        } catch (_: Throwable) {
            null
        }
    }

    // -----------------------------------------------------------------------
    // Web sources
    // -----------------------------------------------------------------------

    private fun ensureWebManager(): WebOverlayManager? {
        val c = compositor ?: return null
        webManager?.let { return it }
        val manager = WebOverlayManager(
            context = context,
            requestTexture = { id, w, h, callback ->
                c.provideWebTexture(id, w, h, callback)
            },
            releaseTexture = { id ->
                c.removeWebTexture(id)
                webFallbacks.remove(id)
                scheduleContentRefresh()
            },
            onFallbackFrame = { id, bitmap ->
                webFallbacks[id] = bitmap
                scheduleContentRefresh()
            },
            onError = { id, message ->
                _state.update {
                    it.copy(
                        webErrors = if (message.isBlank()) it.webErrors - id else it.webErrors + (id to message),
                    )
                }
            },
        )
        webManager = manager
        return manager
    }

    private fun scheduleWebSync() {
        webSyncJob?.cancel()
        webSyncJob = scope.launch {
            delay(450)
            syncWebSources()
        }
    }

    private fun syncWebSources() {
        val c = compositor
        if (c == null) {
            webManager?.release()
            webManager = null
            webFallbacks.clear()
            return
        }
        val enabledWeb = settings.scene.broadcastSources
            .filter { it.type == SourceType.WEB && it.enabled }
        if (enabledWeb.isEmpty()) {
            webManager?.sync(emptyList())
            webFallbacks.clear()
            return
        }
        val manager = ensureWebManager() ?: return
        val video = settings.video
        val specs = enabledWeb.mapNotNull { source ->
            val cfg = source.config as? SourceConfig.Web ?: return@mapNotNull null
            if (cfg.url.isBlank()) return@mapNotNull null
            val rect = source.rect.clamped()
            WebSourceSpec(
                id = source.id,
                url = cfg.url,
                transparent = cfg.transparent,
                zoomPercent = cfg.zoomPercent,
                reloadIntervalSec = cfg.reloadIntervalSec,
                widthPx = (rect.width * video.width).toInt().coerceIn(64, 1920),
                heightPx = (rect.height * video.height).toInt().coerceIn(64, 1920),
            )
        }
        val ids = specs.map { it.id }.toSet()
        webFallbacks.keys.filter { it !in ids }.forEach { webFallbacks.remove(it) }
        manager.sync(specs)
    }

    // -----------------------------------------------------------------------
    // Stats loop
    // -----------------------------------------------------------------------

    private suspend fun tickLoop() {
        while (true) {
            delay(1_000)
            val live = _state.value.isLive
            val frames = framesThisTick.getAndSet(0)
            val bytes = bytesThisTick.getAndSet(0)
            val duration = if (live && liveStartElapsed > 0) {
                SystemClock.elapsedRealtime() - liveStartElapsed
            } else {
                0
            }
            val dropped = if (live) {
                (compositor?.totalDroppedFrames() ?: 0L) + (pipeline?.totalDropped() ?: 0L)
            } else {
                0L
            }
            val cameraCount = compositor?.cameraFrameCount() ?: 0L
            val cameraDelta = (cameraCount - lastCameraFrameSample).coerceAtLeast(0L)
            lastCameraFrameSample = cameraCount
            _state.update {
                it.copy(
                    fps = if (live) frames.toFloat() else 0f,
                    cameraFps = if (previewStarted || it.isLive) cameraDelta.toFloat() else 0f,
                    videoBitrateKbps = if (live) ((bytes * 8) / 1000).toInt() else 0,
                    durationMs = duration,
                    droppedFrames = dropped,
                )
            }
            if (compositor != null) refreshContent()
            checkCameraFrames()
        }
    }

    private fun checkCameraFrames() {
        if (!previewStarted && !_state.value.isLive) return
        if (currentCameraKind == null) return
        val count = compositor?.cameraFrameCount() ?: return
        val now = SystemClock.elapsedRealtime()
        if (count != lastCameraFrameCount) {
            lastCameraFrameCount = count
            lastCameraFrameChangeAt = now
            cameraNoFrameReported = false
            return
        }
        if (now - lastCameraFrameChangeAt > 5_000 && !cameraNoFrameReported) {
            cameraNoFrameReported = true
            postError("Camera is open but no frames are arriving. Try reselecting the camera or replugging the USB cable.")
        }
    }

    // -----------------------------------------------------------------------
    // Service + misc
    // -----------------------------------------------------------------------

    private fun startStreamingService() {
        if (serviceStarted) return
        serviceStarted = true
        try {
            StreamingService.start(context)
        } catch (t: Throwable) {
            serviceStarted = false
            Log.w(TAG, "could not start foreground service", t)
            postError("Background streaming service could not start: ${t.message}")
        }
    }

    private fun stopStreamingService() {
        if (!serviceStarted) return
        serviceStarted = false
        try {
            StreamingService.stop(context)
        } catch (_: Throwable) {
        }
    }

    private val compositorListener = object : VideoCompositor.Listener {
        override fun onCameraTextureReady(texture: SurfaceTexture) = Unit
        override fun onWebTextureReady(sourceId: String, texture: SurfaceTexture) = Unit
        override fun onError(message: String) = postError(message)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun postError(message: String) {
        Log.w(TAG, message)
        _state.update { it.copy(error = message) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** Called by the foreground service when it is destroyed by the system. */
    fun onServiceStopped() {
        serviceStarted = false
    }

    fun updateUvcDeviceKey(key: String?) {
        repository.updateCamera { it.copy(kind = CameraKind.USB_UVC, uvcDeviceKey = key) }
    }

    fun clearWebError(sourceId: String) {
        _state.update { it.copy(webErrors = it.webErrors - sourceId) }
    }
}
