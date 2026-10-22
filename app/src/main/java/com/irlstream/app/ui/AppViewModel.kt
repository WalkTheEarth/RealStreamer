package com.irlstream.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.irlstream.app.RealStreamerApp
import com.irlstream.app.engine.StreamEngine
import com.irlstream.core.model.AppSettings
import com.irlstream.core.model.AudioSettings
import com.irlstream.core.model.CameraKind
import com.irlstream.core.model.CameraSettings
import com.irlstream.core.model.ChatSettings
import com.irlstream.core.model.ConsolePanel
import com.irlstream.core.model.ConsoleSettings
import com.irlstream.core.model.Destination
import com.irlstream.core.model.LensFacing
import com.irlstream.core.model.SceneSource
import com.irlstream.core.model.SourceAudience
import com.irlstream.core.model.SourceType
import com.irlstream.core.model.Sources
import com.irlstream.core.model.VideoSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class Screen { Studio, Destinations, Overlays, Settings }

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RealStreamerApp
    private val repository = app.settings
    private val engine = StreamEngine.get()

    val settings: StateFlow<AppSettings> = repository.settings
    val engineState = engine.state

    private val _screen = MutableStateFlow(Screen.Studio)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun open(screen: Screen) {
        _screen.value = screen
    }

    fun back(): Boolean {
        return if (_screen.value != Screen.Studio) {
            _screen.value = Screen.Studio
            true
        } else {
            false
        }
    }

    fun completeOnboarding(openDestinations: Boolean) {
        repository.update { it.copy(onboardingComplete = true) }
        if (openDestinations) _screen.value = Screen.Destinations
    }

    fun showToast(message: String) {
        _toast.value = message
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun clearError() = engine.clearError()

    // ---- Destinations ------------------------------------------------------

    fun saveDestination(destination: Destination) {
        repository.upsertDestination(destination)
    }

    fun deleteDestination(id: String) {
        repository.deleteDestination(id)
    }

    fun setDestinationEnabled(id: String, enabled: Boolean) {
        repository.setDestinationEnabled(id, enabled)
    }

    // ---- Overlay sources ---------------------------------------------------

    fun addBroadcastSource(type: SourceType): String {
        val source = Sources.broadcast(type, repository.nextOrder(SourceAudience.BROADCAST))
        repository.addSource(source)
        return source.id
    }

    fun addStreamerPanel(panel: ConsolePanel): String? {
        val existing = settings.value.scene.streamerSources.any { source ->
            (source.config as? com.irlstream.core.model.SourceConfig.Console)?.panel == panel
        }
        if (existing) {
            showToast("That streamer panel already exists")
            return null
        }
        val source = Sources.streamerConsole(panel, repository.nextOrder(SourceAudience.STREAMER_ONLY))
        repository.addSource(source)
        return source.id
    }

    fun updateSource(source: SceneSource) = repository.updateSource(source)
    fun deleteSource(id: String) = repository.deleteSource(id)
    fun toggleSource(id: String, enabled: Boolean) = repository.toggleSource(id, enabled)
    fun moveSource(id: String, direction: Int) = repository.moveSource(id, direction)

    // ---- Settings ----------------------------------------------------------

    fun updateVideo(transform: (VideoSettings) -> VideoSettings) = repository.updateVideo(transform)
    fun updateAudio(transform: (AudioSettings) -> AudioSettings) = repository.updateAudio(transform)
    fun updateCamera(transform: (CameraSettings) -> CameraSettings) = repository.updateCamera(transform)
    fun updateChat(transform: (ChatSettings) -> ChatSettings) = repository.updateChat(transform)
    fun updateConsole(transform: (ConsoleSettings) -> ConsoleSettings) = repository.updateConsole(transform)

    fun setMuted(muted: Boolean) = repository.updateAudio { it.copy(muted = muted) }

    // ---- Camera ------------------------------------------------------------

    fun selectCamera(
        kind: CameraKind,
        lens: LensFacing = settings.value.camera.lens,
        uvcKey: String? = settings.value.camera.uvcDeviceKey,
        cameraId: String? = settings.value.camera.cameraId,
    ) {
        engine.selectCamera(kind, lens, uvcKey, cameraId)
    }

    fun switchCamera() = engine.switchCamera()

    fun refreshUvc() = engine.refreshUvc()

    fun setZoom(ratio: Float) = engine.setZoom(ratio)

    fun setTorch(enabled: Boolean) = engine.setTorch(enabled)

    // ---- Live --------------------------------------------------------------

    fun goLive() = engine.goLive()
    fun stopLive() = engine.stopLive()
    fun startPreview() = engine.startPreview()

    // ---- Image import ------------------------------------------------------

    fun importImage(uri: Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = try {
                val dir = File(app.filesDir, "overlays").apply { mkdirs() }
                val file = File(dir, "overlay-${UUID.randomUUID()}.img")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file.absolutePath
            } catch (t: Throwable) {
                null
            }
            withContext(Dispatchers.Main) { onDone(path) }
        }
    }
}
