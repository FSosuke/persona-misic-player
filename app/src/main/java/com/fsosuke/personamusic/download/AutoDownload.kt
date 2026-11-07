package com.fsosuke.personamusic.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fsosuke.personamusic.PersonaApp
import com.fsosuke.personamusic.data.AppSettings
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Keeps hearted YouTube songs downloaded: runs every few hours and right after a heart,
 * optionally only on Wi-Fi (unmetered networks).
 */
class AutoDownload(private val context: Context, private val settings: AppSettings) {

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(if (settings.autoDownloadWifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    /** (Re)schedules the periodic job according to the current settings. */
    fun schedule() {
        val work = WorkManager.getInstance(context)
        if (!settings.autoDownloadLiked.value || settings.downloadFolder.value == null) {
            work.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoDownloadWorker>(6, TimeUnit.HOURS).setConstraints(constraints()).build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Runs soon (e.g. after a heart), respecting the Wi-Fi setting. */
    fun runSoon() {
        if (!settings.autoDownloadLiked.value || settings.downloadFolder.value == null) return
        val request = OneTimeWorkRequestBuilder<AutoDownloadWorker>().setConstraints(constraints()).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val PERIODIC = "auto-download-liked"
        const val ONCE = "auto-download-liked-now"
    }
}

class AutoDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PersonaApp).container
        if (!container.settings.autoDownloadLiked.value || container.settings.downloadFolder.value == null) return Result.success()
        container.downloads.enqueue(container.repository.likedYoutubeSongs())
        // Keep the worker (and so the process) alive while the downloads run, up to ~9 minutes.
        repeat(9 * 60 / 5) {
            if (container.downloads.tasks.value.values.none { it.error == null }) return Result.success()
            delay(5_000)
        }
        return Result.success()
    }
}
