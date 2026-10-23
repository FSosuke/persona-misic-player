package com.fsosuke.personamusic

import android.content.Context
import com.fsosuke.personamusic.data.AppDatabase
import com.fsosuke.personamusic.data.AppSettings
import com.fsosuke.personamusic.data.BackupManager
import com.fsosuke.personamusic.data.LocalMusicScanner
import com.fsosuke.personamusic.data.LyricsRepository
import com.fsosuke.personamusic.data.MusicRepository
import com.fsosuke.personamusic.playback.PlayerConnection
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
    val repository = MusicRepository(database.musicDao(), LocalMusicScanner(context))
    val youTube = YouTubeMusic(httpClient, settings)
    val sponsorBlock = SponsorBlock(httpClient)
    val lyrics = LyricsRepository(httpClient)
    val innerTube = InnerTube(httpClient, context)
    val ytAccounts = YtAccountManager(context, innerTube)
    val ytLibrary = YtMusicLibrary(innerTube, ytAccounts, appScope)
    val player = PlayerConnection(context)
    val backup = BackupManager(context, database, settings)
    val updates = UpdateManager(context, httpClient, appScope)
}
