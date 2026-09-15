package org.communitypoke.fdroidai

import android.app.Application
import org.communitypoke.fdroidai.di.AppContainer

class FdroidAiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
