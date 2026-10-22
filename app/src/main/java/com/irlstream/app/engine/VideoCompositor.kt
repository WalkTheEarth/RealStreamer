package com.irlstream.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLES11Ext
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.irlstream.core.model.ImageFit
import com.irlstream.core.model.Scene
import com.irlstream.core.model.SceneSource
import com.irlstream.core.model.SourceConfig
import com.irlstream.core.model.SourceType
import java.util.concurrent.atomic.AtomicLong

data class OverlayContent(
    val chat: List<ChatMessage> = emptyList(),
    val stats: StatsSnapshot = StatsSnapshot(),
    val tokens: Map<String, String> = emptyMap(),
    val images: Map<String, Bitmap> = emptyMap(),
)

/**
 * Owns the GL scene. A single EGL context renders the camera + broadcast overlays into
 * an offscreen texture, then blits that texture to the WYSIWYG preview and (when live)
 * the hardware encoder's input surface.
 */
class VideoCompositor(
    private val context: Context,
    private val outputWidth: Int,
    private val outputHeight: Int,
    private val fps: Int,
    private val listener: Listener,
) {
    interface Listener {
        fun onCameraTextureReady(texture: SurfaceTexture)
        fun onWebTextureReady(sourceId: String, texture: SurfaceTexture)
        fun onError(message: String)
    }

    private val glThread = HandlerThread("irl-compositor").apply { start() }
    private val glHandler = Handler(glThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var egl: EglCore? = null
    private var texture2dProgram: GlProgram? = null
    private var textureOesProgram: GlProgram? = null

    private var fbo = 0
    private var fboTexture = 0

    private var previewSurface: Surface? = null
    private var previewEglSurface: android.opengl.EGLSurface? = null
    private var encoderSurface: Surface? = null
    private var encoderEglSurface: android.opengl.EGLSurface? = null

    private var cameraTextureId = 0
    private var cameraSurfaceTexture: SurfaceTexture? = null
    private var cameraFrameAvailable = false
    private var cameraSourceWidth = 0
    private var cameraSourceHeight = 0
    private var cameraRotation = 0
    private var cameraMirror = false

    private var scene: Scene = Scene()
    private var content: OverlayContent = OverlayContent()
    private val layers = HashMap<String, LayerTexture>()
    private val webLayers = HashMap<String, WebLayer>()

    private var rendering = false
    private var ready = false
    private var released = false

    @Volatile
    private var epochNanos: Long = 0
    @Volatile
    private var droppedFrames: Long = 0
    private var lastFrameNanos: Long = 0
    private var lastDropReported = 0L

    private val positionBuffer = BufferPool.floats(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    private val texBuffer = BufferPool.floats(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    private val identity = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
    private val stMatrix = FloatArray(16)

    private val renderRunnable = object : Runnable {
        override fun run() {
            if (!rendering) return
            val started = SystemClock.elapsedRealtimeNanos()
            try {
                renderFrame()
            } catch (t: Throwable) {
                Log.e(TAG, "render failed", t)
                listener.onError("Preview renderer error: ${t.message}")
                rendering = false
                return
            }
            val frameInterval = 1_000_000_000L / fps.coerceAtLeast(1)
            val elapsed = SystemClock.elapsedRealtimeNanos() - started
            val delayNanos = (frameInterval - elapsed).coerceAtLeast(1_000_000L)
            glHandler.postDelayed(this, delayNanos / 1_000_000)
        }
    }

    fun start() {
        glHandler.post {
            if (released) return@post
            try {
                egl = EglCore().apply { initialize() }
                texture2dProgram = GlProgram(VERTEX_SHADER, FRAGMENT_2D)
                textureOesProgram = GlProgram(VERTEX_SHADER, FRAGMENT_OES)
                createFbo()
                ready = true
                maybeStartLoop()
            } catch (t: Throwable) {
                Log.e(TAG, "compositor init failed", t)
                listener.onError("GPU compositor unavailable: ${t.message}")
            }
        }
    }

    fun setPreviewSurface(surface: Surface?) {
        glHandler.post {
            if (released) return@post
            if (previewSurface === surface && surface != null) return@post
            previewEglSurface?.let { egl?.destroySurface(it) }
            previewEglSurface = null
            previewSurface = surface
            if (surface != null && egl != null) {
                previewEglSurface = egl?.createWindowSurface(surface)
            }
            maybeStartLoop()
            if (previewEglSurface == null && surface != null) {
                Log.w(TAG, "Could not create preview EGL surface")
            }
        }
    }

    fun setEncoderSurface(surface: Surface?) {
        glHandler.post {
            if (released) return@post
            if (encoderSurface === surface && surface != null) return@post
            encoderEglSurface?.let { egl?.destroySurface(it) }
            encoderEglSurface = null
            encoderSurface = surface
            if (surface != null && egl != null) {
                encoderEglSurface = egl?.createWindowSurface(surface)
            }
            maybeStartLoop()
            if (encoderEglSurface == null && surface != null) {
                Log.w(TAG, "Could not create encoder EGL surface")
            }
        }
    }

    fun setScene(scene: Scene) {
        glHandler.post {
            this.scene = scene
            pruneLayers()
        }
    }

    fun setContent(content: OverlayContent) {
        glHandler.post {
            this.content = content
        }
    }

    fun setStreamEpoch(nanos: Long) {
        epochNanos = nanos
    }

    fun setCameraSourceInfo(width: Int, height: Int, rotationDegrees: Int, mirror: Boolean) {
        glHandler.post {
            cameraSourceWidth = width
            cameraSourceHeight = height
            cameraRotation = rotationDegrees
            cameraMirror = mirror
        }
    }

    fun totalDroppedFrames(): Long = droppedFrames

    fun provideCameraTexture(
        width: Int,
        height: Int,
        rotationDegrees: Int,
        mirror: Boolean,
        callback: (SurfaceTexture) -> Unit,
    ) {
        glHandler.post {
            if (released || !ready) return@post
            if (cameraSurfaceTexture == null) {
                cameraTextureId = GlTextures.createOes()
                cameraSurfaceTexture = SurfaceTexture(cameraTextureId).apply {
                    setOnFrameAvailableListener({ cameraFrameAvailable = true }, glHandler)
                }
            }
            cameraSurfaceTexture?.setDefaultBufferSize(width.coerceAtLeast(2), height.coerceAtLeast(2))
            cameraSourceWidth = width
            cameraSourceHeight = height
            cameraRotation = rotationDegrees
            cameraMirror = mirror
            val st = cameraSurfaceTexture ?: return@post
            mainHandler.post { callback(st) }
        }
    }

    fun provideWebTexture(sourceId: String, width: Int, height: Int, callback: (SurfaceTexture) -> Unit) {
        glHandler.post {
            if (released || !ready) return@post
            val w = width.coerceIn(16, 4096)
            val h = height.coerceIn(16, 4096)
            val existing = webLayers[sourceId]
            if (existing != null && existing.width == w && existing.height == h) {
                mainHandler.post { callback(existing.surfaceTexture) }
                return@post
            }
            existing?.release()
            val textureId = GlTextures.createOes()
            val st = SurfaceTexture(textureId)
            st.setDefaultBufferSize(w, h)
            val layer = WebLayer(sourceId, textureId, st, w, h)
            webLayers[sourceId] = layer
            mainHandler.post { callback(st) }
        }
    }

    fun removeWebTexture(sourceId: String) {
        glHandler.post { webLayers.remove(sourceId)?.release() }
    }

    fun release() {
        released = true
        glHandler.post { releaseInternal() }
        try {
            glThread.quitSafely()
            glThread.join(1500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    private fun maybeStartLoop() {
        val shouldRender = previewEglSurface != null || encoderEglSurface != null
        if (shouldRender && !rendering) {
            rendering = true
            lastFrameNanos = SystemClock.elapsedRealtimeNanos()
            glHandler.post(renderRunnable)
        } else if (!shouldRender) {
            rendering = false
            glHandler.removeCallbacks(renderRunnable)
        }
    }

    private fun renderFrame() {
        val egl = egl ?: return
        if (!egl.makeCurrentPbuffer()) return
        updateSurfaceTextures()

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        val bg = scene.backgroundArgb.toInt()
        GLES20.glClearColor(
            ((bg shr 16) and 0xFF) / 255f,
            ((bg shr 8) and 0xFF) / 255f,
            (bg and 0xFF) / 255f,
            ((bg ushr 24) and 0xFF) / 255f,
        )
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        drawCamera()
        updateLayers()
        drawLayers()

        val now = SystemClock.elapsedRealtimeNanos()
        val interval = 1_000_000_000L / fps.coerceAtLeast(1)
        if (lastFrameNanos != 0L) {
            val delta = now - lastFrameNanos
            if (delta > interval * 2) {
                droppedFrames += (delta / interval) - 1
                val total = droppedFrames
                if (total - lastDropReported >= 5) lastDropReported = total
            }
        }
        lastFrameNanos = now

        // Streaming output first: it matters more than the local preview.
        encoderEglSurface?.let { present(egl, it, now) }
        previewEglSurface?.let { present(egl, it, null) }
    }

    private fun present(egl: EglCore, surface: android.opengl.EGLSurface, nowNanos: Long?) {
        if (!egl.makeCurrent(surface)) return
        val w = egl.surfaceWidth(surface).coerceAtLeast(1)
        val h = egl.surfaceHeight(surface).coerceAtLeast(1)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glDisable(GLES20.GL_BLEND)
        val program = texture2dProgram ?: return
        GLES20.glUseProgram(program.id)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexture)
        GLES20.glUniform1i(program.uTexture, 0)
        GLES20.glUniform1f(program.uAlpha, 1f)
        GLES20.glUniformMatrix4fv(program.uTexMatrix, 1, false, identity, 0)
        drawQuad(program, 0f, 0f, w.toFloat(), h.toFloat(), flipY = false)
        if (surface == encoderEglSurface) {
            val epoch = epochNanos
            val pts = if (epoch > 0 && nowNanos != null) (nowNanos - epoch).coerceAtLeast(0) else 0
            egl.setPresentationTime(surface, pts)
        }
        egl.swap(surface)
        egl.makeCurrentPbuffer()
    }

    private fun updateSurfaceTextures() {
        if (cameraFrameAvailable) {
            cameraFrameAvailable = false
            try {
                cameraSurfaceTexture?.updateTexImage()
            } catch (t: Throwable) {
                Log.w(TAG, "camera updateTexImage failed", t)
            }
        }
        webLayers.values.forEach { layer ->
            if (layer.frameAvailable) {
                layer.frameAvailable = false
                try {
                    layer.surfaceTexture.updateTexImage()
                } catch (t: Throwable) {
                    Log.w(TAG, "web updateTexImage failed", t)
                }
            }
        }
    }

    private fun drawCamera() {
        val st = cameraSurfaceTexture ?: return
        val tex = cameraTextureId
        if (tex == 0) return
        val program = textureOesProgram ?: return
        st.getTransformMatrix(stMatrix)
        val layer = createCameraLayerMatrix(cameraRotation, cameraMirror)
        val matrix = multiplyMatrix(stMatrix, layer)

        val rotated = ((cameraRotation % 360) + 360) % 360 == 90 || ((cameraRotation % 360) + 360) % 360 == 270
        val sourceAspect = if (cameraSourceWidth > 0 && cameraSourceHeight > 0) {
            if (rotated) cameraSourceHeight.toFloat() / cameraSourceWidth.toFloat()
            else cameraSourceWidth.toFloat() / cameraSourceHeight.toFloat()
        } else {
            outputWidth.toFloat() / outputHeight.toFloat()
        }
        val rect = coverRect(outputWidth.toFloat(), outputHeight.toFloat(), sourceAspect)

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(program.id)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        GLES20.glUniform1i(program.uTexture, 0)
        GLES20.glUniform1f(program.uAlpha, 1f)
        GLES20.glUniformMatrix4fv(program.uTexMatrix, 1, false, matrix, 0)
        drawQuad(program, rect.left, rect.top, rect.right, rect.bottom, flipY = false)
    }

    private fun updateLayers() {
        val scale = outputHeight / 1080f
        val activeIds = HashSet<String>()

        for (source in scene.broadcastSources) {
            if (!source.enabled) continue
            val rect = sourcePixels(source)
            if (rect.width() < 4 || rect.height() < 4) continue
            val w = rect.width().toInt()
            val h = rect.height().toInt()
            when (source.type) {
                SourceType.TEXT -> {
                    val config = source.config as? SourceConfig.Text ?: continue
                    val key = textKey(source, config, w, h)
                    activeIds += source.id
                    val entry = layers.getOrPut(source.id) { LayerTexture(GlTextures.create2d()) }
                    if (entry.key != key) {
                        val bitmap = OverlayRenderer.text(config, w, h, content.tokens, scale)
                        upload(entry, bitmap, key)
                        bitmap?.recycle()
                    }
                }
                SourceType.CHAT -> {
                    val config = source.config as? SourceConfig.Chat ?: continue
                    val key = chatKey(source, config, w, h)
                    activeIds += source.id
                    val entry = layers.getOrPut(source.id) { LayerTexture(GlTextures.create2d()) }
                    if (entry.key != key) {
                        val bitmap = OverlayRenderer.chat(config, content.chat, w, h, scale)
                        upload(entry, bitmap, key)
                        bitmap?.recycle()
                    }
                }
                SourceType.STATS -> {
                    val config = source.config as? SourceConfig.Stats ?: continue
                    val key = statsKey(source, config, w, h, content.stats)
                    activeIds += source.id
                    val entry = layers.getOrPut(source.id) { LayerTexture(GlTextures.create2d()) }
                    if (entry.key != key) {
                        val bitmap = OverlayRenderer.stats(config, content.stats, w, h, scale)
                        upload(entry, bitmap, key)
                        bitmap?.recycle()
                    }
                }
                SourceType.IMAGE -> {
                    val bitmap = content.images[source.id] ?: continue
                    val key = "img:${System.identityHashCode(bitmap)}:${bitmap.width}x${bitmap.height}:$w:$h"
                    activeIds += source.id
                    val entry = layers.getOrPut(source.id) { LayerTexture(GlTextures.create2d()) }
                    if (entry.key != key) {
                        upload(entry, bitmap, key)
                        entry.bitmap = bitmap
                    }
                }
                SourceType.WEB -> {
                    activeIds += source.id
                    // Web layers are OES textures managed separately; a fallback bitmap
                    // may be supplied if the platform cannot render a virtual display.
                    val fallback = content.images[source.id]
                    if (webLayers[source.id] == null && fallback != null) {
                        val key = "webfb:${System.identityHashCode(fallback)}:$w:$h"
                        val entry = layers.getOrPut(source.id) { LayerTexture(GlTextures.create2d()) }
                        if (entry.key != key) {
                            upload(entry, fallback, key)
                            entry.bitmap = fallback
                        }
                    }
                }
                else -> Unit
            }
        }

        val iterator = layers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in activeIds) {
                GlTextures.delete(entry.value.textureId)
                iterator.remove()
            }
        }
        // Web textures are released by the engine after it tears down the offscreen
        // WebView/capture that produces into them, never from the render loop.
    }

    private fun drawLayers() {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val program = texture2dProgram ?: return
        for (source in scene.broadcastSources) {
            if (!source.enabled) continue
            val rect = sourcePixels(source)
            when (source.type) {
                SourceType.TEXT, SourceType.CHAT, SourceType.STATS -> {
                    val entry = layers[source.id] ?: continue
                    if (!entry.valid) continue
                    GLES20.glUseProgram(program.id)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, entry.textureId)
                    GLES20.glUniform1i(program.uTexture, 0)
                    GLES20.glUniform1f(program.uAlpha, 1f)
                    GLES20.glUniformMatrix4fv(program.uTexMatrix, 1, false, identity, 0)
                    drawQuad(program, rect.left, rect.top, rect.right, rect.bottom, flipY = true)
                }
                SourceType.IMAGE -> {
                    val entry = layers[source.id] ?: continue
                    if (!entry.valid) continue
                    val bitmap = entry.bitmap ?: continue
                    val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val cover = (source.config as? SourceConfig.Image)?.fit == ImageFit.COVER
                    val fit = if (cover) coverRect(rect.width(), rect.height(), aspect) else containedRect(rect, aspect)
                    val dst = if (cover) {
                        android.graphics.RectF(rect.left + fit.left, rect.top + fit.top, rect.left + fit.right, rect.top + fit.bottom)
                    } else {
                        fit
                    }
                    if (cover) {
                        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
                        val sx = rect.left.toInt().coerceAtLeast(0)
                        val sy = (outputHeight - rect.bottom.toInt()).coerceAtLeast(0)
                        val sw = rect.width().toInt().coerceAtLeast(1)
                        val sh = rect.height().toInt().coerceAtLeast(1)
                        GLES20.glScissor(sx, sy, sw, sh)
                    }
                    GLES20.glUseProgram(program.id)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, entry.textureId)
                    GLES20.glUniform1i(program.uTexture, 0)
                    GLES20.glUniform1f(program.uAlpha, 1f)
                    GLES20.glUniformMatrix4fv(program.uTexMatrix, 1, false, identity, 0)
                    drawQuad(program, dst.left, dst.top, dst.right, dst.bottom, flipY = true)
                    if (cover) {
                        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
                    }
                }
                SourceType.WEB -> {
                    val web = webLayers[source.id]
                    if (web != null) {
                        val oes = textureOesProgram ?: continue
                        GLES20.glUseProgram(oes.id)
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, web.textureId)
                        GLES20.glUniform1i(oes.uTexture, 0)
                        GLES20.glUniform1f(oes.uAlpha, 1f)
                        web.surfaceTexture.getTransformMatrix(stMatrix)
                        GLES20.glUniformMatrix4fv(oes.uTexMatrix, 1, false, stMatrix, 0)
                        drawQuad(oes, rect.left, rect.top, rect.right, rect.bottom, flipY = false)
                    } else {
                        val entry = layers[source.id] ?: continue
                        if (!entry.valid) continue
                        val bitmap = entry.bitmap ?: continue
                        GLES20.glUseProgram(program.id)
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, entry.textureId)
                        GLES20.glUniform1i(program.uTexture, 0)
                        GLES20.glUniform1f(program.uAlpha, 1f)
                        GLES20.glUniformMatrix4fv(program.uTexMatrix, 1, false, identity, 0)
                        drawQuad(program, rect.left, rect.top, rect.right, rect.bottom, flipY = true)
                    }
                }
                else -> Unit
            }
        }
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawQuad(
        program: GlProgram,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        flipY: Boolean,
    ) {
        val l = left / outputWidth * 2f - 1f
        val r = right / outputWidth * 2f - 1f
        val t = 1f - top / outputHeight * 2f
        val b = 1f - bottom / outputHeight * 2f
        positionBuffer.clear()
        positionBuffer.put(floatArrayOf(l, b, r, b, l, t, r, t)).position(0)
        texBuffer.clear()
        if (flipY) {
            texBuffer.put(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
        } else {
            texBuffer.put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
        }
        texBuffer.position(0)

        GLES20.glEnableVertexAttribArray(program.aPosition)
        GLES20.glVertexAttribPointer(program.aPosition, 2, GLES20.GL_FLOAT, false, 0, positionBuffer)
        GLES20.glEnableVertexAttribArray(program.aTexCoord)
        GLES20.glVertexAttribPointer(program.aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(program.aPosition)
        GLES20.glDisableVertexAttribArray(program.aTexCoord)
    }

    private fun upload(entry: LayerTexture, bitmap: Bitmap?, key: String) {
        if (bitmap == null) {
            entry.key = key
            entry.valid = false
            return
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, entry.textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        entry.key = key
        entry.valid = true
    }

    private fun pruneLayers() {
        val ids = scene.broadcastSources.map { it.id }.toSet()
        val iterator = layers.entries.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            if (e.key !in ids) {
                GlTextures.delete(e.value.textureId)
                iterator.remove()
            }
        }
    }

    private fun sourcePixels(source: SceneSource): RectF {
        val r = source.rect.clamped()
        return RectF(
            r.x * outputWidth,
            r.y * outputHeight,
            (r.x + r.width) * outputWidth,
            (r.y + r.height) * outputHeight,
        )
    }

    private fun coverRect(canvasW: Float, canvasH: Float, aspect: Float): RectF {
        if (aspect <= 0f) return RectF(0f, 0f, canvasW, canvasH)
        val canvasAspect = canvasW / canvasH
        return if (aspect > canvasAspect) {
            val h = canvasH
            val w = h * aspect
            RectF((canvasW - w) / 2f, 0f, (canvasW + w) / 2f, h)
        } else {
            val w = canvasW
            val h = w / aspect
            RectF(0f, (canvasH - h) / 2f, w, (canvasH + h) / 2f)
        }
    }

    private fun containedRect(bounds: RectF, aspect: Float): RectF {
        val bw = bounds.width()
        val bh = bounds.height()
        val boundsAspect = bw / bh
        return if (aspect > boundsAspect) {
            val w = bw
            val h = w / aspect
            val top = bounds.top + (bh - h) / 2f
            RectF(bounds.left, top, bounds.right, top + h)
        } else {
            val h = bh
            val w = h * aspect
            val left = bounds.left + (bw - w) / 2f
            RectF(left, bounds.top, left + w, bounds.bottom)
        }
    }

    private fun textKey(source: SceneSource, config: SourceConfig.Text, w: Int, h: Int): String {
        val resolved = if (config.dynamic) {
            content.tokens.entries.fold(config.text) { acc, (k, v) -> acc.replace("{$k}", v) }
        } else {
            config.text
        }
        return "text:${source.id}:$w:$h:${config.hashCode()}:${resolved.hashCode()}"
    }

    private fun chatKey(source: SceneSource, config: SourceConfig.Chat, w: Int, h: Int): String {
        val messages = content.chat.takeLast(config.maxMessages.coerceIn(1, 20))
        val digest = messages.joinToString("|") { "${it.id}:${it.author.hashCode()}:${it.text.hashCode()}" }
        return "chat:${source.id}:$w:$h:${config.hashCode()}:${digest.hashCode()}"
    }

    private fun statsKey(source: SceneSource, config: SourceConfig.Stats, w: Int, h: Int, stats: StatsSnapshot): String {
        return "stats:${source.id}:$w:$h:${config.hashCode()}:${stats.fps}:${stats.bitrateKbps}:${stats.droppedFrames}:${stats.uptimeMs / 1000}:${stats.destinationsLive}:${stats.destinationsTotal}"
    }

    private fun createFbo() {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        fboTexture = tex[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, outputWidth, outputHeight, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val buf = IntArray(1)
        GLES20.glGenFramebuffers(1, buf, 0)
        fbo = buf[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTexture, 0,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw IllegalStateException("FBO incomplete: 0x${Integer.toHexString(status)}")
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun releaseInternal() {
        rendering = false
        glHandler.removeCallbacks(renderRunnable)
        previewEglSurface?.let { egl?.destroySurface(it) }
        encoderEglSurface?.let { egl?.destroySurface(it) }
        previewEglSurface = null
        encoderEglSurface = null
        cameraSurfaceTexture?.release()
        cameraSurfaceTexture = null
        if (cameraTextureId != 0) GlTextures.delete(cameraTextureId)
        webLayers.values.forEach { it.release() }
        webLayers.clear()
        layers.values.forEach { GlTextures.delete(it.textureId) }
        layers.clear()
        texture2dProgram?.release()
        textureOesProgram?.release()
        if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (fboTexture != 0) GlTextures.delete(fboTexture)
        egl?.release()
        egl = null
        ready = false
    }

    private inner class WebLayer(
        val id: String,
        val textureId: Int,
        val surfaceTexture: SurfaceTexture,
        val width: Int,
        val height: Int,
    ) {
        @Volatile
        var frameAvailable: Boolean = false

        init {
            surfaceTexture.setOnFrameAvailableListener({ frameAvailable = true }, glHandler)
        }

        fun release() {
            try {
                surfaceTexture.setOnFrameAvailableListener(null)
                surfaceTexture.release()
            } catch (_: Throwable) {
            }
            GlTextures.delete(textureId)
        }
    }

    private class LayerTexture(val textureId: Int) {
        var key: String = ""
        var bitmap: Bitmap? = null
        var valid: Boolean = false
    }

    companion object {
        private const val TAG = "VideoCompositor"

        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """

        private const val FRAGMENT_2D = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform float uAlpha;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord) * uAlpha;
            }
        """

        private const val FRAGMENT_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            uniform float uAlpha;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord) * uAlpha;
            }
        """
    }
}
