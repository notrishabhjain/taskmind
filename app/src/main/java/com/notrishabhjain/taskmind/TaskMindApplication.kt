package com.notrishabhjain.taskmind

import android.app.Application
import androidx.work.Configuration
import com.notrishabhjain.taskmind.di.AppContainer

class TaskMindApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(container.captureProcessingWorkerFactory)
            .build()
}
