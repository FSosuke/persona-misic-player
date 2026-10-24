package com.fsosuke.personamusic.release

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fsosuke.personamusic.MainActivity
import com.fsosuke.personamusic.PersonaApp
import com.fsosuke.personamusic.R
import com.fsosuke.personamusic.data.AppSettings
import com.fsosuke.personamusic.data.Confidants
import com.fsosuke.personamusic.data.MusicDao
import com.fsosuke.personamusic.youtube.YtItem
import com.fsosuke.personamusic.youtube.YtMusicLibrary
import com.fsosuke.personamusic.youtube.YtPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A newly spotted album or single. */
data class Release(
    val id: String,
    val title: String,
    val artist: String,
    val subtitle: String?,
    val thumbnail: String?,
    val detectedAt: Long,
) {
    fun toPlaylist() = YtPlaylist(id, title, subtitle ?: artist, thumbnail)
}

/**
 * Release Radar: periodically looks at the artist pages of your confidants and followed
 * artists and reports albums/singles that weren't there at the last check.
 */
class ReleaseRadar(
    private val context: Context,
    private val ytLibrary: YtMusicLibrary,
    private val dao: MusicDao,
    private val settings: AppSettings,
) {
    private val prefs = context.getSharedPreferences("release_radar", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _followed = MutableStateFlow(prefs.getStringSet(KEY_FOLLOWED, emptySet()).orEmpty())
    val followed: StateFlow<Set<String>> = _followed.asStateFlow()

    private val _releases = MutableStateFlow(loadReleases())
    /** Releases found in the last [KEEP_DAYS] days, newest first. */
    val releases: StateFlow<List<Release>> = _releases.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    fun isFollowed(artist: String) = artist.lowercase() in _followed.value.map { it.lowercase() }

    fun toggleFollow(artist: String) {
        val current = _followed.value
        val next = if (isFollowed(artist)) current.filterNot { it.equals(artist, ignoreCase = true) }.toSet() else current + artist
        prefs.edit().putStringSet(KEY_FOLLOWED, next).apply()
        _followed.value = next
    }

    /** Runs a check now; returns the releases that are new since the last one. */
    suspend fun check(): List<Release> = mutex.withLock {
        _checking.value = true
        try {
            val confidants = Confidants.fromCounts(dao.artistPlayCountsNow()).filter { it.rank >= MIN_RANK }.take(MAX_CONFIDANTS)
            val artists = (followed.value + confidants.map { it.artist }).distinctBy { it.lowercase() }
            val seen = loadSeen()
            val found = mutableListOf<Release>()
            val now = System.currentTimeMillis()
            for (artist in artists) {
                val page = runCatching { ytLibrary.artist(artist, null) }.getOrNull() ?: continue
                val collections = page.shelves.flatMap { it.items }.filterIsInstance<YtItem.Collection>()
                    .map { it.playlist }
                    .filter { it.id.startsWith("MPRE") }
                    .distinctBy { it.id }
                val key = artist.lowercase()
                val known = seen[key]
                if (known != null) {
                    collections.filter { it.id !in known }.forEach { p ->
                        found += Release(p.id, p.title, page.name, p.subtitle, p.thumbnail, now)
                    }
                }
                // First time we look at an artist we only remember what exists – no flood of "new" items.
                seen[key] = (known.orEmpty() + collections.map { it.id }).toSet()
            }
            saveSeen(seen)
            if (found.isNotEmpty()) {
                val all = (found + _releases.value).distinctBy { it.id }
                    .filter { now - it.detectedAt < KEEP_DAYS * DAY_MS }
                saveReleases(all)
                _releases.value = all
            }
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
            found
        } finally {
            _checking.value = false
        }
    }

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<ReleaseRadarWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun notify(found: List<Release>) {
        if (found.isEmpty() || !settings.releaseRadar.value) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Release Radar", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (found.size == 1) "New from ${found.first().artist}" else "${found.size} new releases from your confidants"
        val text = found.take(4).joinToString(" · ") { "${it.title} (${it.artist})" }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun loadSeen(): MutableMap<String, Set<String>> = runCatching {
        val json = JSONObject(prefs.getString(KEY_SEEN, "{}")!!)
        json.keys().asSequence().associateWith { key ->
            val array = json.getJSONArray(key)
            (0 until array.length()).mapTo(HashSet()) { array.getString(it) }
        }.toMutableMap<String, Set<String>>()
    }.getOrDefault(mutableMapOf())

    private fun saveSeen(seen: Map<String, Set<String>>) {
        val json = JSONObject()
        seen.forEach { (k, v) -> json.put(k, JSONArray(v.toList())) }
        prefs.edit().putString(KEY_SEEN, json.toString()).apply()
    }

    private fun loadReleases(): List<Release> = runCatching {
        val array = JSONArray(prefs.getString(KEY_RELEASES, "[]"))
        val now = System.currentTimeMillis()
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Release(
                o.getString("id"), o.getString("title"), o.getString("artist"),
                o.optString("subtitle").ifEmpty { null }, o.optString("thumbnail").ifEmpty { null }, o.getLong("detectedAt"),
            )
        }.filter { now - it.detectedAt < KEEP_DAYS * DAY_MS }
    }.getOrDefault(emptyList())

    private fun saveReleases(list: List<Release>) {
        val array = JSONArray(
            list.map {
                JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist)
                    .put("subtitle", it.subtitle ?: "").put("thumbnail", it.thumbnail ?: "").put("detectedAt", it.detectedAt)
            }
        )
        prefs.edit().putString(KEY_RELEASES, array.toString()).apply()
    }

    private companion object {
        const val KEY_FOLLOWED = "followed"
        const val KEY_SEEN = "seen"
        const val KEY_RELEASES = "releases"
        const val KEY_LAST_CHECK = "last_check"
        const val WORK_NAME = "release-radar"
        const val CHANNEL = "release_radar"
        const val NOTIFICATION_ID = 4242
        const val MIN_RANK = 2
        const val MAX_CONFIDANTS = 25
        const val KEEP_DAYS = 30
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

/** Background job: runs the Release Radar check and posts a notification for new finds. */
class ReleaseRadarWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val radar = (applicationContext as PersonaApp).container.releaseRadar
        return runCatching { radar.notify(radar.check()) }.fold({ Result.success() }, { Result.retry() })
    }
}
