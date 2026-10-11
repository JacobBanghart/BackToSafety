package com.backtosafety.app

import android.app.Application
import com.backtosafety.app.core.CoreStartup

class NijiiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The Rust core loads on its own thread while the first screen is set up.
        CoreStartup.begin(this)
    }
}
