package com.galib.step

import android.app.Application
import com.galib.step.data.work.SyncWorker
import com.galib.step.service.StepTrackingService
import kotlinx.coroutines.launch

class StepApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.ensureInit(this)
        Graph.notifier.ensureChannels()
        SyncWorker.schedule(this)
        // Resurrect background tracking / live island if the user has them enabled
        Graph.appScope.launch {
            val p = Graph.prefs.snapshot()
            if (p.backgroundTracking) {
                runCatching { StepTrackingService.start(this@StepApp) }
            }
        }
    }
}
