package com.fsosuke.personamusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which Persona game the UI accent colours are borrowed from. */
enum class PersonaTheme(val label: String) {
    P5("Persona 5 · Rebellion Red"),
    P4("Persona 4 · Midnight Yellow"),
    P3("Persona 3 · Moonlight Blue"),
    ROYAL("Persona 5 Royal · Crimson"),
}

enum class AudioQuality(val label: String) {
    BEST("Best available"),
    DATA_SAVER("Data saver"),
}

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        runCatching { PersonaTheme.valueOf(prefs.getString(KEY_THEME, null)!!) }.getOrDefault(PersonaTheme.P5)
    )
    val theme: StateFlow<PersonaTheme> = _theme.asStateFlow()

    private val _quality = MutableStateFlow(
        runCatching { AudioQuality.valueOf(prefs.getString(KEY_QUALITY, null)!!) }.getOrDefault(AudioQuality.BEST)
    )
    val quality: StateFlow<AudioQuality> = _quality.asStateFlow()

    private val _skipNonMusic = MutableStateFlow(prefs.getBoolean(KEY_SKIP_NON_MUSIC, true))
    /** SponsorBlock "music_offtopic" skipping, the way ReVanced does it for YouTube Music. */
    val skipNonMusic: StateFlow<Boolean> = _skipNonMusic.asStateFlow()

    private val _autoplay = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, true))
    /** Keep the music going with similar songs when the queue runs out. */
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    private val _syncLikes = MutableStateFlow(prefs.getBoolean(KEY_SYNC_LIKES, true))
    val syncLikes: StateFlow<Boolean> = _syncLikes.asStateFlow()

    private val _reportHistory = MutableStateFlow(prefs.getBoolean(KEY_REPORT_HISTORY, true))
    /** Add plays to the YouTube history so YT Music recommendations keep learning. */
    val reportHistory: StateFlow<Boolean> = _reportHistory.asStateFlow()

    fun setAutoplay(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()
        _autoplay.value = value
    }

    fun setSyncLikes(value: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_LIKES, value).apply()
        _syncLikes.value = value
    }

    fun setReportHistory(value: Boolean) {
        prefs.edit().putBoolean(KEY_REPORT_HISTORY, value).apply()
        _reportHistory.value = value
    }

    private val _japaneseOnly = MutableStateFlow(prefs.getBoolean(KEY_JAPANESE_ONLY, false))
    /** Only recommend / auto-play Japanese music (plus artists the user already listens to). */
    val japaneseOnly: StateFlow<Boolean> = _japaneseOnly.asStateFlow()

    fun setJapaneseOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_JAPANESE_ONLY, value).apply()
        _japaneseOnly.value = value
    }

    private val _downloadFolder = MutableStateFlow(prefs.getString(KEY_DOWNLOAD_FOLDER, null))
    /** SAF tree uri of the folder downloads are saved to; null until the user picks one. */
    val downloadFolder: StateFlow<String?> = _downloadFolder.asStateFlow()

    fun setDownloadFolder(uri: String?) {
        prefs.edit().putString(KEY_DOWNLOAD_FOLDER, uri).apply()
        _downloadFolder.value = uri
    }

    private val _playerBackground = MutableStateFlow(prefs.getString(KEY_PLAYER_BG, null))
    /** Optional user image (file path) behind the player, shown as P5 duotone. */
    val playerBackground: StateFlow<String?> = _playerBackground.asStateFlow()

    private val _homeBackground = MutableStateFlow(prefs.getString(KEY_HOME_BG, null))
    val homeBackground: StateFlow<String?> = _homeBackground.asStateFlow()

    fun setPlayerBackground(path: String?) {
        prefs.edit().putString(KEY_PLAYER_BG, path).apply()
        _playerBackground.value = path
    }

    fun setHomeBackground(path: String?) {
        prefs.edit().putString(KEY_HOME_BG, path).apply()
        _homeBackground.value = path
    }

    fun setTheme(value: PersonaTheme) {
        prefs.edit().putString(KEY_THEME, value.name).apply()
        _theme.value = value
    }

    fun setQuality(value: AudioQuality) {
        prefs.edit().putString(KEY_QUALITY, value.name).apply()
        _quality.value = value
    }

    fun setSkipNonMusic(value: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_NON_MUSIC, value).apply()
        _skipNonMusic.value = value
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_QUALITY = "quality"
        const val KEY_SKIP_NON_MUSIC = "skip_non_music"
        const val KEY_PLAYER_BG = "player_background"
        const val KEY_AUTOPLAY = "autoplay"
        const val KEY_JAPANESE_ONLY = "japanese_only"
        const val KEY_DOWNLOAD_FOLDER = "download_folder"
        const val KEY_SYNC_LIKES = "sync_likes"
        const val KEY_REPORT_HISTORY = "report_history"
        const val KEY_HOME_BG = "home_background"
    }
}
