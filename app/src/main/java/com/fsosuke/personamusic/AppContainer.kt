package com.fsosuke.personamusic

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.fsosuke.personamusic.data.AppDatabase
import com.fsosuke.personamusic.data.AppSettings
import com.fsosuke.personamusic.data.BackupManager
import com.fsosuke.personamusic.data.ContentFilter
import com.fsosuke.personamusic.data.LocalMusicScanner
import com.fsosuke.personamusic.data.LyricsRepository
import com.fsosuke.personamusic.data.MusicRepository
import com.fsosuke.personamusic.data.SearchHistory
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.download.DownloadManager
import com.fsosuke.personamusic.playback.PlayerConnection
import com.fsosuke.personamusic.playback.SleepTimer
import com.fsosuke.personamusic.data.RankUp
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
    val innerTube = InnerTube(httpClient, context)
    val ytAccounts = YtAccountManager(context, innerTube)
    val ytLibrary = YtMusicLibrary(innerTube, ytAccounts, youTube, appScope)
    val player = PlayerConnection(context)
    val sleepTimer = SleepTimer()

    /** Latest confidant rank-up, shown as an overlay until dismissed (survives the app being closed). */
    val pendingRankUp = MutableStateFlow<RankUp?>(null)
    val backup = BackupManager(context, database, settings)
    val searchHistory = SearchHistory(context)
    val contentFilter = ContentFilter(database.musicDao(), settings, appScope)
    val downloads = DownloadManager(context, httpClient, youTube, database.musicDao(), settings, appScope)
    val updates = UpdateManager(context, httpClient, appScope)

    /** Heart a song locally and, for YouTube songs, on the signed-in YouTube Music account too. */
    suspend fun setLiked(song: Song, liked: Boolean) {
        repository.setLiked(song, liked)
        if (settings.syncLikes.value && ytAccounts.cookie != null) {
            runCatching { ytLibrary.rate(song, liked) }
        }
    }
}
