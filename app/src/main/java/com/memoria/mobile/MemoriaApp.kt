package com.memoria.mobile

import android.app.Application
import com.memoria.mobile.di.AppGraph

/** Application entry point — owns the dependency graph for the process lifetime. */
class MemoriaApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
