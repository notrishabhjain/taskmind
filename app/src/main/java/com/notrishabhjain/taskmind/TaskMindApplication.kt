package com.notrishabhjain.taskmind

import android.annotation.SuppressLint
import android.app.Application
import androidx.work.Configuration
import com.notrishabhjain.taskmind.di.AppContainer

@SuppressLint("RemoveWorkManagerInitializer")
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
