package com.fsosuke.personamusic.radio

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fsosuke.personamusic.MainActivity
import com.fsosuke.personamusic.R
import com.fsosuke.personamusic.data.MusicRepository
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.youtube.YtItem
import com.fsosuke.personamusic.youtube.YtMusicLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Starred radio stations, from any area. */
class RadioFavourites(context: Context) {
    private val prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)
    private val _stations = MutableStateFlow(load())
    val stations: StateFlow<List<RadikoStation>> = _stations.asStateFlow()

    fun isFavourite(id: String) = _stations.value.any { it.id == id }

    fun toggle(station: RadikoStation) {
        val current = _stations.value
        _stations.value = if (current.any { it.id == station.id }) current.filterNot { it.id == station.id }
        else current + station.copy(program = null)
        save()
    }

    private fun load(): List<RadikoStation> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { i -> array.getJSONObject(i).toStation() }
    }.getOrDefault(emptyList())

    private fun save() {
        val array = JSONArray()
        _stations.value.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "favourite_stations"
    }
}

internal fun RadikoStation.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("logo", logo).put("area", area.id)

internal fun JSONObject.toStation(): RadikoStation? {
    val area = RadikoArea.byId(optString("area")) ?: return null
    return RadikoStation(getString("id"), getString("name"), null, getString("logo"), area)
}

/**
 * "Radio finds": a song heard on the radio is looked up on YouTube Music, liked and collected
 * in a local playlist of that name.
 */
class RadioFinds(
    private val repository: MusicRepository,
    private val ytLibrary: YtMusicLibrary,
    private val like: suspend (Song) -> Unit,
) {
    private val _saved = MutableStateFlow<Set<String>>(emptySet())
    /** Radio titles saved in this session (station song title + artist). */
    val saved: StateFlow<Set<String>> = _saved.asStateFlow()

    fun key(title: String, artist: String?) = "$title|${artist.orEmpty()}"

    /** Returns the matched YouTube Music song, or null if nothing fitting was found. */
    suspend fun save(title: String, artist: String?): Song? {
        val results = ytLibrary.search(listOfNotNull(title, artist).joinToString(" "))
        val song = (results.top as? YtItem.SongItem)?.song ?: results.songs.firstOrNull() ?: return null
        like(song)
        val playlistId = repository.playlistIdByName(PLAYLIST) ?: repository.createPlaylist(PLAYLIST)
        repository.addToPlaylist(playlistId, song)
        _saved.value = _saved.value + key(title, artist)
        return song
    }

    companion object {
        const val PLAYLIST = "Radio finds"
    }
}

/** "Remind me" for radio programmes: a notification five minutes before they start. */
class RadioReminders(private val context: Context) {
    private val prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)
    private val _keys = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    fun key(station: RadikoStation, program: RadikoProgram) = "${station.id}@${program.start}"

    fun toggle(station: RadikoStation, program: RadikoProgram) {
        val key = key(station, program)
        val work = WorkManager.getInstance(context)
        if (key in _keys.value) {
            work.cancelUniqueWork(workName(key))
            update(_keys.value - key)
            return
        }
        val start = program.start ?: return
        val startMillis = start.atZone(ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli()
        val delay = (startMillis - LEAD_MS - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<RadioReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "key" to key,
                    "station" to station.toJson().toString(),
                    "title" to program.title,
                    "time" to program.timeRange,
                ),
            )
            .build()
        work.enqueueUniqueWork(workName(key), ExistingWorkPolicy.REPLACE, request)
        update(_keys.value + key)
    }

    internal fun done(key: String) = update(_keys.value - key)

    private fun update(keys: Set<String>) {
        _keys.value = keys
        prefs.edit().putStringSet(KEY, keys).apply()
    }

    internal fun notify(key: String, station: RadikoStation, title: String, time: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Radio reminders", NotificationManager.IMPORTANCE_HIGH))
        }
        val listen = PendingIntent.getActivity(
            context,
            key.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_PLAY_STATION)
                .putExtra(EXTRA_STATION, station.toJson().toString())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$title starts soon")
            .setContentText(listOfNotNull(station.name, time).joinToString(" · "))
            .setContentIntent(listen)
            .addAction(0, "Listen", listen)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(key.hashCode(), notification) }
    }

    companion object {
        const val ACTION_PLAY_STATION = "com.fsosuke.personamusic.PLAY_STATION"
        const val EXTRA_STATION = "station"
        private const val KEY = "reminders"
        private const val CHANNEL = "radio_reminders"
        private const val LEAD_MS = 5 * 60 * 1000L

        private fun workName(key: String) = "radio-reminder-$key"

        /** Station from a reminder notification's intent. */
        fun stationFrom(intent: Intent?): RadikoStation? =
            intent?.takeIf { it.action == ACTION_PLAY_STATION }
                ?.getStringExtra(EXTRA_STATION)
                ?.let { runCatching { JSONObject(it).toStation() }.getOrNull() }
    }
}

class RadioReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val reminders = (applicationContext as com.fsosuke.personamusic.PersonaApp).container.radioReminders
        val key = inputData.getString("key") ?: return Result.success()
        val station = inputData.getString("station")?.let { runCatching { JSONObject(it).toStation() }.getOrNull() }
        if (station != null) reminders.notify(key, station, inputData.getString("title").orEmpty(), inputData.getString("time"))
        reminders.done(key)
        return Result.success()
    }
}
