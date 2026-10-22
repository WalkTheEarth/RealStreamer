package com.irlstream.app

import android.app.Application
import com.irlstream.app.data.SettingsRepository
import com.irlstream.app.engine.StreamEngine

class RealStreamerApp : Application() {
    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        StreamEngine.initialize(this, settings)
    }
}
