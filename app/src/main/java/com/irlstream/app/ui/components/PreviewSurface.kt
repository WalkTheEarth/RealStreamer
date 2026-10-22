package com.irlstream.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.irlstream.app.engine.StreamEngine
import com.irlstream.app.ui.theme.IRL
import com.irlstream.core.model.NormalizedRect
import com.irlstream.core.model.Orientation
import com.irlstream.core.model.SceneSource
import com.irlstream.core.model.VideoSettings
import kotlin.math.abs

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun LockStreamOrientation(video: VideoSettings) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, video.orientation) {
        activity.requestedOrientation = if (video.orientation == Orientation.LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

private class PreviewSurfaceRef {
    var surface: Surface? = null
}

/**
 * Preview consumer wired to the engine compositor.
 *
 * This deliberately uses a [TextureView], not a SurfaceView. A SurfaceView inside a
 * Compose AndroidView gets its own window layer and can show black on many devices even
 * while the compositor is swapping frames (the camera runs, but the hole-punch never
 * resolves through the Compose render node). A TextureView is drawn as part of the normal
 * window, so the compositor's EGL output is always visible and Compose content can sit on
 * top of it. The extra GPU copy is irrelevant at preview resolutions.
 */
@Composable
fun EngineSurface(modifier: Modifier = Modifier) {
    val engine = remember { StreamEngine.get() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val surfaceRef = remember { PreviewSurfaceRef() }

    AndroidView(
        factory = { ctx ->
            val container = FrameLayout(ctx)
            container.setBackgroundColor(AndroidColor.BLACK)
            val textureView = TextureView(ctx).apply {
                isOpaque = true
                addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
                    android.util.Log.i(
                        "EngineSurface",
                        "textureLayout ${right - left}x${bottom - top} at ($left,$top) view=${view.width}x${view.height}",
                    )
                }
                // Compose controls and the overlay editor sit above this view.
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        android.util.Log.i("EngineSurface", "textureAvailable ${width}x$height")
                        val surface = Surface(surfaceTexture)
                        surfaceRef.surface = surface
                        engine.attachPreview(surface)
                        engine.startPreview()
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        android.util.Log.i("EngineSurface", "textureSizeChanged ${width}x$height")
                        surfaceRef.surface?.takeIf { it.isValid }?.let { engine.onPreviewSurfaceChanged(it) }
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        android.util.Log.i("EngineSurface", "textureDestroyed")
                        surfaceRef.surface?.let {
                            engine.detachPreview(it)
                            it.release()
                        }
                        surfaceRef.surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
            }
            container.addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
                android.util.Log.i(
                    "EngineSurface",
                    "containerLayout ${right - left}x${bottom - top} at ($left,$top) view=${view.width}x${view.height}",
                )
            }
            container.addView(
                textureView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            container
        },
        modifier = modifier.clipToBounds(),
    )

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    surfaceRef.surface?.takeIf { it.isValid }?.let {
                        engine.attachPreview(it)
                        engine.startPreview()
                    }
                }
                Lifecycle.Event.ON_STOP -> engine.onHostPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            surfaceRef.surface?.let {
                engine.detachPreview(it)
                it.release()
            }
            surfaceRef.surface = null
        }
    }
}

@Composable
fun EnginePreview(aspectRatio: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(IRL.Ink),
    ) {
        EngineSurface(Modifier.fillMaxSize())
    }
}

/**
 * The live broadcast preview with draggable/resizable layer boxes drawn on top.
 */
@Composable
fun EditablePreview(
    aspectRatio: Float,
    sources: List<SceneSource>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onRectChange: (String, NormalizedRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(IRL.Ink),
    ) {
        EngineSurface(Modifier.fillMaxSize())
        OverlayEditorLayer(
            sources = sources,
            selectedId = selectedId,
            onSelect = onSelect,
            onRectChange = onRectChange,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private enum class DragMode { MOVE, RESIZE }

@Composable
private fun OverlayEditorLayer(
    sources: List<SceneSource>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onRectChange: (String, NormalizedRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var transientRects by remember { mutableStateOf<Map<String, NormalizedRect>>(emptyMap()) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnRectChange by rememberUpdatedState(onRectChange)
    val ordered = remember(sources) { sources.sortedBy { it.order } }
    val rendered = remember(ordered, transientRects) {
        ordered.map { source -> source.copy(rect = transientRects[source.id] ?: source.rect.clamped()) }
    }

    Canvas(
        modifier
            .onSizeChanged { viewport = it }
            .pointerInput(ordered, selectedId, viewport) {
                if (viewport.width <= 0 || viewport.height <= 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val nx = down.position.x / viewport.width
                    val ny = down.position.y / viewport.height
                    val hit = ordered.asReversed().firstOrNull { it.rect.clamped().contains(nx, ny) }
                    if (hit == null) {
                        currentOnSelect(null)
                        return@awaitEachGesture
                    }
                    currentOnSelect(hit.id)
                    val rect = hit.rect.clamped()
                    val cornerX = (rect.x + rect.width) * viewport.width
                    val cornerY = (rect.y + rect.height) * viewport.height
                    var mode = if (abs(down.position.x - cornerX) < 36.dp.toPx() &&
                        abs(down.position.y - cornerY) < 36.dp.toPx()
                    ) {
                        DragMode.RESIZE
                    } else {
                        DragMode.MOVE
                    }
                    var current = rect
                    var last = down.position
                    transientRects = transientRects + (hit.id to current)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.position - last
                        last = change.position
                        if (delta.x != 0f || delta.y != 0f) {
                            val dx = delta.x / viewport.width
                            val dy = delta.y / viewport.height
                            current = when (mode) {
                                DragMode.MOVE -> current.copy(
                                    x = (current.x + dx).coerceIn(0f, 1f - current.width),
                                    y = (current.y + dy).coerceIn(0f, 1f - current.height),
                                )
                                DragMode.RESIZE -> current.copy(
                                    width = (current.width + dx).coerceIn(0.05f, 1f - current.x),
                                    height = (current.height + dy).coerceIn(0.05f, 1f - current.y),
                                )
                            }
                            transientRects = transientRects + (hit.id to current)
                            currentOnRectChange(hit.id, current)
                            change.consume()
                        }
                    }
                    transientRects = transientRects - hit.id
                }
            },
    ) {
        if (viewport == IntSize.Zero) return@Canvas
        val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
        rendered.forEach { source ->
            val rect = source.rect.clamped()
            val topLeft = Offset(rect.x * size.width, rect.y * size.height)
            val boxSize = ComposeSize(rect.width * size.width, rect.height * size.height)
            val selected = source.id == selectedId
            if (selected) {
                drawRect(
                    color = IRL.Signal,
                    topLeft = topLeft,
                    size = boxSize,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                val handles = listOf(
                    Offset(topLeft.x, topLeft.y),
                    Offset(topLeft.x + boxSize.width, topLeft.y),
                    Offset(topLeft.x, topLeft.y + boxSize.height),
                    Offset(topLeft.x + boxSize.width, topLeft.y + boxSize.height),
                )
                val handleSize = 7.dp.toPx()
                handles.forEach { point ->
                    drawRect(
                        color = IRL.Signal,
                        topLeft = Offset(point.x - handleSize / 2f, point.y - handleSize / 2f),
                        size = ComposeSize(handleSize, handleSize),
                    )
                    drawRect(
                        color = IRL.Ink,
                        topLeft = Offset(point.x - handleSize / 4f, point.y - handleSize / 4f),
                        size = ComposeSize(handleSize / 2f, handleSize / 2f),
                    )
                }
            } else {
                drawRect(
                    color = if (source.enabled) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
                    topLeft = topLeft,
                    size = boxSize,
                    style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                )
            }
        }
    }
}
