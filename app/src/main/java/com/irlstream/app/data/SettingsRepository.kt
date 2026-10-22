package com.irlstream.app.data

import android.content.Context
import android.util.Log
import com.irlstream.core.model.AppSettings
import com.irlstream.core.model.Destination
import com.irlstream.core.model.Scene
import com.irlstream.core.model.SceneSource
import com.irlstream.core.model.SourceAudience
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single-profile settings store. Private app storage, debounced JSON writes.
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "irlstream-settings.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private var saveJob: Job? = null
    private val saveVersion = AtomicInteger(0)
    private val saveMutex = Mutex()

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        val version = saveVersion.incrementAndGet()
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            saveMutex.withLock {
                if (version == saveVersion.get()) persist(next)
            }
        }
    }

    // ---- Destinations -----------------------------------------------------

    fun upsertDestination(destination: Destination) = update { current ->
        val exists = current.destinations.any { it.id == destination.id }
        current.copy(
            destinations = if (exists) {
                current.destinations.map { if (it.id == destination.id) destination else it }
            } else {
                current.destinations + destination
            },
        )
    }

    fun deleteDestination(id: String) = update { current ->
        current.copy(destinations = current.destinations.filterNot { it.id == id })
    }

    fun setDestinationEnabled(id: String, enabled: Boolean) = update { current ->
        current.copy(
            destinations = current.destinations.map {
                if (it.id == id) it.copy(enabled = enabled) else it
            },
        )
    }

    // ---- Scene ------------------------------------------------------------

    fun updateScene(transform: (Scene) -> Scene) = update { current ->
        current.copy(scene = transform(current.scene))
    }

    fun addSource(source: SceneSource) = updateScene { scene -> scene.upsert(source) }

    fun updateSource(source: SceneSource) = updateScene { scene -> scene.upsert(source) }

    fun deleteSource(id: String) = updateScene { scene -> scene.remove(id) }

    fun toggleSource(id: String, enabled: Boolean) = updateScene { scene ->
        val source = scene.source(id) ?: return@updateScene scene
        scene.upsert(source.copy(enabled = enabled))
    }

    fun moveSource(id: String, direction: Int) = updateScene { scene ->
        val audienceSources = scene.sources.sortedBy { it.order }
        val index = audienceSources.indexOfFirst { it.id == id }
        if (index < 0) return@updateScene scene
        // Reorder within the audience group only.
        val source = audienceSources[index]
        val group = audienceSources.filter { it.audience == source.audience }
        val groupIndex = group.indexOfFirst { it.id == id }
        val swapWith = groupIndex + direction
        if (swapWith !in group.indices) return@updateScene scene
        val other = group[swapWith]
        val reordered = scene.sources.map {
            when (it.id) {
                source.id -> it.copy(order = other.order)
                other.id -> it.copy(order = source.order)
                else -> it
            }
        }
        scene.copy(sources = reordered)
    }

    fun nextOrder(audience: SourceAudience): Int {
        val scene = _settings.value.scene
        return (scene.sources.filter { it.audience == audience }.maxOfOrNull { it.order } ?: -1) + 1
    }

    // ---- Misc -------------------------------------------------------------

    fun updateChat(transform: (com.irlstream.core.model.ChatSettings) -> com.irlstream.core.model.ChatSettings) =
        update { it.copy(chat = transform(it.chat)) }

    fun updateConsole(transform: (com.irlstream.core.model.ConsoleSettings) -> com.irlstream.core.model.ConsoleSettings) =
        update { it.copy(console = transform(it.console)) }

    fun updateVideo(transform: (com.irlstream.core.model.VideoSettings) -> com.irlstream.core.model.VideoSettings) =
        update { it.copy(video = transform(it.video)) }

    fun updateAudio(transform: (com.irlstream.core.model.AudioSettings) -> com.irlstream.core.model.AudioSettings) =
        update { it.copy(audio = transform(it.audio)) }

    fun updateCamera(transform: (com.irlstream.core.model.CameraSettings) -> com.irlstream.core.model.CameraSettings) =
        update { it.copy(camera = transform(it.camera)) }

    fun load(): AppSettings {
        if (!file.exists()) return AppSettings()
        return try {
            json.decodeFromString<AppSettings>(file.readText())
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read settings, starting fresh", t)
            try {
                file.renameTo(File(appContext.filesDir, "irlstream-settings.corrupt.json"))
            } catch (_: Throwable) {
            }
            AppSettings()
        }
    }

    private fun persist(settings: AppSettings) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(AppSettings.serializer(), settings))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to persist settings", t)
        }
    }

    companion object {
        private const val TAG = "SettingsRepository"
    }
}
