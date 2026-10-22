package com.irlstream.app.engine

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal class EglCore {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
    private val surfaces = HashSet<EGLSurface>()
    private var initialized = false

    fun initialize() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) throw IllegalStateException("No EGL display")
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw IllegalStateException("eglInitialize failed: ${EGL14.eglGetError()}")
        }

        val baseAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        config = chooseConfig(baseAttribs + intArrayOf(EGLExt.EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE))
            ?: chooseConfig(baseAttribs)
            ?: throw IllegalStateException("No suitable EGL config")

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) throw IllegalStateException("eglCreateContext failed: ${EGL14.eglGetError()}")

        val pbufferAttribs = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE,
        )
        pbuffer = EGL14.eglCreatePbufferSurface(display, config, pbufferAttribs, 0)
        if (pbuffer == EGL14.EGL_NO_SURFACE) throw IllegalStateException("pbuffer failed")
        if (!makeCurrent(pbuffer)) throw IllegalStateException("eglMakeCurrent(pbuffer) failed")
        initialized = true
    }

    private fun chooseConfig(attribs: IntArray): EGLConfig? {
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        val ok = EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
        return if (ok && num[0] > 0) configs[0] else null
    }

    fun makeCurrent(surface: EGLSurface): Boolean =
        EGL14.eglMakeCurrent(display, surface, surface, context)

    fun makeCurrentPbuffer(): Boolean = makeCurrent(pbuffer)

    fun createWindowSurface(surface: Surface): EGLSurface? {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, attribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            return null
        }
        surfaces += eglSurface
        return eglSurface
    }

    fun surfaceWidth(surface: EGLSurface): Int {
        val value = IntArray(1)
        EGL14.eglQuerySurface(display, surface, EGL14.EGL_WIDTH, value, 0)
        return value[0]
    }

    fun surfaceHeight(surface: EGLSurface): Int {
        val value = IntArray(1)
        EGL14.eglQuerySurface(display, surface, EGL14.EGL_HEIGHT, value, 0)
        return value[0]
    }

    fun setPresentationTime(surface: EGLSurface, nanos: Long) {
        EGLExt.eglPresentationTimeANDROID(display, surface, nanos)
    }

    fun swap(surface: EGLSurface): Boolean = EGL14.eglSwapBuffers(display, surface)

    fun destroySurface(surface: EGLSurface?) {
        if (surface == null || surface == EGL14.EGL_NO_SURFACE) return
        surfaces.remove(surface)
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
        }
    }

    fun release() {
        if (!initialized) return
        surfaces.toList().forEach { destroySurface(it) }
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (pbuffer != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, pbuffer)
            pbuffer = EGL14.EGL_NO_SURFACE
        }
        if (context != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(display, context)
            context = EGL14.EGL_NO_CONTEXT
        }
        EGL14.eglTerminate(display)
        display = EGL14.EGL_NO_DISPLAY
        initialized = false
    }

    companion object {
        private const val TAG = "EglCore"
    }
}

internal class GlProgram(vertexSource: String, fragmentSource: String) {
    val id: Int
    val aPosition: Int
    val aTexCoord: Int
    val uTexMatrix: Int
    val uTexture: Int
    val uAlpha: Int

    init {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        id = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
            val status = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] != GLES20.GL_TRUE) {
                val log = GLES20.glGetProgramInfoLog(it)
                GLES20.glDeleteProgram(it)
                throw IllegalStateException("Program link failed: $log")
            }
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        aPosition = GLES20.glGetAttribLocation(id, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(id, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(id, "uTexMatrix")
        uTexture = GLES20.glGetUniformLocation(id, "uTexture")
        uAlpha = GLES20.glGetUniformLocation(id, "uAlpha")
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Shader compile failed: $log\n$source")
        }
        return shader
    }

    fun release() {
        GLES20.glDeleteProgram(id)
    }
}

internal object GlTextures {
    fun createOes(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return id
    }

    fun create2d(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return id
    }

    fun delete(id: Int) {
        GLES20.glDeleteTextures(1, intArrayOf(id), 0)
    }
}

internal object BufferPool {
    fun floats(vararg values: Float): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values)
            position(0)
        }
}

internal fun createCameraLayerMatrix(rotationDegrees: Int, mirror: Boolean): FloatArray {
    val rotation = when (((rotationDegrees % 360) + 360) % 360) {
        90 -> floatArrayOf(
            0f, -1f, 0f, 0f,
            1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f,
        )
        180 -> floatArrayOf(
            -1f, 0f, 0f, 0f,
            0f, -1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            1f, 1f, 0f, 1f,
        )
        270 -> floatArrayOf(
            0f, 1f, 0f, 0f,
            -1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f,
            1f, 0f, 0f, 1f,
        )
        else -> floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
    }
    if (!mirror) return rotation
    val mirrorMatrix = floatArrayOf(
        -1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        1f, 0f, 0f, 1f,
    )
    val out = FloatArray(16)
    android.opengl.Matrix.multiplyMM(out, 0, mirrorMatrix, 0, rotation, 0)
    return out
}

internal fun multiplyMatrix(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(16)
    android.opengl.Matrix.multiplyMM(out, 0, a, 0, b, 0)
    return out
}
