package com.fsosuke.personamusic

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.fsosuke.personamusic.data.AppDatabase
import com.fsosuke.personamusic.data.AppSettings
import com.fsosuke.personamusic.data.AutoBackup
import com.fsosuke.personamusic.data.BackupManager
import com.fsosuke.personamusic.data.ContentFilter
import com.fsosuke.personamusic.data.Dictionary
import com.fsosuke.personamusic.data.ListeningHistory
import com.fsosuke.personamusic.data.LocalMusicScanner
import com.fsosuke.personamusic.data.LyricsRepository
import com.fsosuke.personamusic.data.Translator
import com.fsosuke.personamusic.data.MusicRepository
import com.fsosuke.personamusic.data.SearchHistory
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.download.AutoDownload
import com.fsosuke.personamusic.download.DownloadManager
import com.fsosuke.personamusic.playback.AudioEffects
import com.fsosuke.personamusic.playback.PlayerConnection
import com.fsosuke.personamusic.release.ReleaseRadar
import com.fsosuke.personamusic.playback.SleepTimer
import com.fsosuke.personamusic.data.RankUp
import com.fsosuke.personamusic.data.SocialStats
import com.fsosuke.personamusic.data.StatUp
import kotlinx.coroutines.flow.MutableStateFlow
import com.fsosuke.personamusic.update.UpdateManager
import com.fsosuke.personamusic.youtube.InnerTube
import com.fsosuke.personamusic.youtube.SponsorBlock
import com.fsosuke.personamusic.youtube.YtAccountManager
import com.fsosuke.personamusic.youtube.YtMusicLibrary
import com.fsosuke.personamusic.youtube.YouTubeMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    /** For fire-and-forget writes that must outlive the screen that started them. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = AppSettings(context)
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    val database = AppDatabase.create(context)
    val repository = MusicRepository(
        database.musicDao(),
        LocalMusicScanner(context) {
            // Downloads get their own card, so keep them out of the local library.
            settings.downloadFolder.value?.let { uri ->
                runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)) }.getOrNull()
                    ?.takeIf { it.startsWith("primary:") }
                    ?.substringAfter(':')
                    ?.let { if (it.isEmpty()) null else "$it/" }
            }
        },
    )
    val youTube = YouTubeMusic(httpClient, settings)
    val sponsorBlock = SponsorBlock(httpClient)
    val lyrics = LyricsRepository(httpClient)
    val translator = Translator(httpClient, settings)
    val dictionary = Dictionary(httpClient)
    val innerTube = InnerTube(httpClient, context)
    val ytAccounts = YtAccountManager(context, innerTube)
    val radiko = com.fsosuke.personamusic.radio.Radiko(context, httpClient)
    val ytLibrary = YtMusicLibrary(innerTube, ytAccounts, youTube, appScope) { settings.homeRegion.value.ifEmpty { null } }
    val radioFavourites = com.fsosuke.personamusic.radio.RadioFavourites(context)
    val radioReminders = com.fsosuke.personamusic.radio.RadioReminders(context)
    val radioFinds = com.fsosuke.personamusic.radio.RadioFinds(repository, ytLibrary) { setLiked(it, true) }
    val player = PlayerConnection(context)
    val sleepTimer = SleepTimer()
    val audioEffects = AudioEffects(settings, appScope)

    /** Latest confidant rank-up, shown as an overlay until dismissed (survives the app being closed). */
    val pendingRankUp = MutableStateFlow<RankUp?>(null)

    /** Social stats that just levelled up, shown one after another. */
    val pendingStatUps = MutableStateFlow<List<StatUp>>(emptyList())
    val backup = BackupManager(context, database, settings)
    val autoBackup = AutoBackup(context, backup, settings)
    val searchHistory = SearchHistory(context)
    val contentFilter = ContentFilter(database.musicDao(), settings, appScope)
    val history = ListeningHistory(database.musicDao())
    val socialStats = SocialStats(context, database.musicDao())

    /** Re-checks social stats in the background and queues any "stat up" for display. */
    fun checkStatUps() {
        appScope.launch {
            val ups = runCatching { socialStats.checkForStatUps() }.getOrDefault(emptyList())
            if (ups.isNotEmpty()) pendingStatUps.value = pendingStatUps.value + ups
        }
    }
    val releaseRadar = ReleaseRadar(context, ytLibrary, database.musicDao(), settings)
    val autoDownload = AutoDownload(context, settings)
    val downloads = DownloadManager(context, httpClient, youTube, database.musicDao(), settings, appScope)
    val updates = UpdateManager(context, httpClient, appScope)

    /** Heart a song locally and, for YouTube songs, on the signed-in YouTube Music account too. */
    /** Fires for every newly hearted song (drives the "Take your heart" splash). */
    val likeEvents = kotlinx.coroutines.flow.MutableSharedFlow<Song>(extraBufferCapacity = 1)

    suspend fun setLiked(song: Song, liked: Boolean) {
        repository.setLiked(song, liked)
        if (liked) likeEvents.tryEmit(song)
        if (liked && song.source == com.fsosuke.personamusic.data.SongSource.YOUTUBE) autoDownload.runSoon()
        if (settings.syncLikes.value && ytAccounts.cookie != null) {
            runCatching { ytLibrary.rate(song, liked) }
        }
    }
}
