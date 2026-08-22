package com.notrishabhjain.taskmind

import android.app.Application
import com.notrishabhjain.taskmind.di.AppContainer

class TaskMindApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
