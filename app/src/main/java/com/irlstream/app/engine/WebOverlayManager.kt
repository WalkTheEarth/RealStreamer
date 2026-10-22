package com.irlstream.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.irlstream.app.R

data class WebSourceSpec(
    val id: String,
    val url: String,
    val transparent: Boolean,
    val zoomPercent: Int,
    val reloadIntervalSec: Int,
    val widthPx: Int,
    val heightPx: Int,
) {
    val key: String
        get() = "$id|$url|$transparent|$zoomPercent|$reloadIntervalSec|$widthPx|$heightPx"
}

/**
 * Renders web sources into the broadcast. Opaque pages are composed by a virtual display
 * running an offscreen WebView; transparent overlays use bitmap capture so alpha is
 * preserved (at a lower frame rate).
 */
class WebOverlayManager(
    private val context: Context,
    private val requestTexture: (String, Int, Int, (SurfaceTexture) -> Unit) -> Unit,
    private val releaseTexture: (String) -> Unit,
    private val onFallbackFrame: (String, Bitmap) -> Unit,
    private val onError: (String, String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val captures = HashMap<String, Capture>()
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    fun sync(sources: List<WebSourceSpec>) {
        val wanted = sources.filter { isValidUrl(it.url) }.associateBy { it.id }
        captures.keys.filter { it !in wanted.keys }.forEach { id ->
            captures.remove(id)?.release()
        }
        wanted.values.forEach { spec ->
            val existing = captures[spec.id]
            if (existing != null && existing.key == spec.key) return@forEach
            existing?.release()
            val capture = Capture(spec)
            captures[spec.id] = capture
            capture.start()
        }
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        captures.values.forEach { it.release() }
        captures.clear()
    }

    private fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.length < 8) return false
        return trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("file://") ||
            trimmed.startsWith("data:") ||
            trimmed.startsWith("about:")
    }

    private inner class Capture(val spec: WebSourceSpec) {
        val key: String = spec.key
        private var generation = 0
        private var webView: WebView? = null
        private var presentation: android.app.Presentation? = null
        private var virtualDisplay: VirtualDisplay? = null
        private var surface: android.view.Surface? = null
        private var requestedTexture = false
        private val reloadRunnable = object : Runnable {
            override fun run() {
                webView?.reload()
                if (spec.reloadIntervalSec > 0) main.postDelayed(this, spec.reloadIntervalSec * 1000L)
            }
        }

        fun start() {
            val gen = ++generation
            onError(spec.id, "")
            if (spec.transparent) {
                startFallback()
            } else {
                requestedTexture = true
                requestTexture(spec.id, spec.widthPx, spec.heightPx) { st ->
                    main.post {
                        if (generation != gen) {
                            releaseTexture(spec.id)
                            return@post
                        }
                        startVirtual(st)
                    }
                }
            }
        }

        @android.annotation.SuppressLint("SetJavaScriptEnabled")
        private fun buildWebView(): WebView {
            val view = WebView(context)
            view.setBackgroundColor(Color.TRANSPARENT)
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.settings.mediaPlaybackRequiresUserGesture = false
            view.settings.textZoom = spec.zoomPercent.coerceIn(25, 400)
            view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            view.settings.cacheMode = WebSettings.LOAD_DEFAULT
            view.webViewClient = WebViewClient()
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(spec.widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(spec.heightPx, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, spec.widthPx, spec.heightPx)
            return view
        }

        private fun startVirtual(st: SurfaceTexture) {
            val gen = ++generation
            try {
                val producer = android.view.Surface(st).also { surface = it }
                val vd = createDisplay(producer) ?: throw IllegalStateException("Virtual display unavailable")
                virtualDisplay = vd
                val pres = android.app.Presentation(
                    context,
                    vd.display,
                    R.style.Theme_RealStreamer_WebOverlay,
                )
                pres.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                pres.window?.setDimAmount(0f)
                val view = buildWebView()
                pres.setContentView(view)
                pres.show()
                presentation = pres
                webView = view
                view.loadUrl(spec.url)
                scheduleReload()
                onError(spec.id, "")
            } catch (t: Throwable) {
                Log.w(TAG, "virtual display web source failed; using bitmap capture", t)
                onError(spec.id, "Low-latency capture unavailable — using snapshot mode")
                teardownVirtual()
                releaseTexture(spec.id)
                startFallback()
            }
        }

        private fun createDisplay(producer: android.view.Surface): VirtualDisplay? {
            val dpi = context.resources.displayMetrics.densityDpi
            val flagSets = intArrayOf(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            )
            for (flags in flagSets) {
                try {
                    val vd = displayManager.createVirtualDisplay(
                        "irl-web-${spec.id}",
                        spec.widthPx,
                        spec.heightPx,
                        dpi,
                        producer,
                        flags,
                    )
                    if (vd != null) return vd
                } catch (t: Throwable) {
                    Log.w(TAG, "createVirtualDisplay failed", t)
                }
            }
            return null
        }

        private fun startFallback() {
            val gen = ++generation
            try {
                val view = buildWebView()
                webView = view
                view.loadUrl(spec.url)
                view.resumeTimers()
                scheduleReload()
                val capture = object : Runnable {
                    override fun run() {
                        if (generation != gen) return
                        val wv = webView ?: return
                        try {
                            wv.measure(
                                View.MeasureSpec.makeMeasureSpec(spec.widthPx, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(spec.heightPx, View.MeasureSpec.EXACTLY),
                            )
                            wv.layout(0, 0, spec.widthPx, spec.heightPx)
                            val bitmap = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                            wv.draw(canvas)
                            onFallbackFrame(spec.id, bitmap)
                        } catch (t: Throwable) {
                            Log.w(TAG, "web bitmap capture failed", t)
                            onError(spec.id, "Web capture failed: ${t.message}")
                            return
                        }
                        main.postDelayed(this, 125)
                    }
                }
                main.postDelayed(capture, 500)
            } catch (t: Throwable) {
                Log.w(TAG, "web fallback failed", t)
                onError(spec.id, "Web source failed: ${t.message}")
            }
        }

        private fun scheduleReload() {
            if (spec.reloadIntervalSec > 0) {
                main.postDelayed(reloadRunnable, spec.reloadIntervalSec * 1000L)
            }
        }

        fun release() {
            generation++
            main.removeCallbacks(reloadRunnable)
            teardownVirtual()
            try {
                webView?.stopLoading()
                webView?.loadUrl("about:blank")
                webView?.destroy()
            } catch (_: Throwable) {
            }
            webView = null
            if (requestedTexture) {
                releaseTexture(spec.id)
                requestedTexture = false
            }
        }

        private fun teardownVirtual() {
            try {
                presentation?.dismiss()
            } catch (_: Throwable) {
            }
            presentation = null
            try {
                virtualDisplay?.release()
            } catch (_: Throwable) {
            }
            virtualDisplay = null
            try {
                surface?.release()
            } catch (_: Throwable) {
            }
            surface = null
        }
    }

    companion object {
        private const val TAG = "WebOverlayManager"
    }
}
