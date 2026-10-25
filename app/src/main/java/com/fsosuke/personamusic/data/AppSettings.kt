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

    private val _autoDownloadLiked = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DOWNLOAD_LIKED, false))
    /** Download hearted YouTube songs automatically. */
    val autoDownloadLiked: StateFlow<Boolean> = _autoDownloadLiked.asStateFlow()
    private val _autoDownloadWifiOnly = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DOWNLOAD_WIFI, true))
    val autoDownloadWifiOnly: StateFlow<Boolean> = _autoDownloadWifiOnly.asStateFlow()

    fun setAutoDownloadLiked(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_LIKED, value).apply()
        _autoDownloadLiked.value = value
    }

    fun setAutoDownloadWifiOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_WIFI, value).apply()
        _autoDownloadWifiOnly.value = value
    }

    private val _translateTo = MutableStateFlow(prefs.getString(KEY_TRANSLATE_TO, "en") ?: "en")
    /** Target language of lyric translations ("en" or "de"). */
    val translateTo: StateFlow<String> = _translateTo.asStateFlow()

    fun setTranslateTo(value: String) {
        prefs.edit().putString(KEY_TRANSLATE_TO, value).apply()
        _translateTo.value = value
    }

    private val _eqEnabled = MutableStateFlow(prefs.getBoolean(KEY_EQ_ENABLED, false))
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()
    private val _eqLevels = MutableStateFlow(prefs.getString(KEY_EQ_LEVELS, "").orEmpty())
    /** Equalizer band levels in millibel, comma separated ("" = flat). */
    val eqLevels: StateFlow<String> = _eqLevels.asStateFlow()
    private val _normalize = MutableStateFlow(prefs.getBoolean(KEY_NORMALIZE, false))
    /** Evens out loud and quiet songs (compressor + limiter). */
    val normalize: StateFlow<Boolean> = _normalize.asStateFlow()
    private val _crossfadeSeconds = MutableStateFlow(prefs.getInt(KEY_CROSSFADE, 0))
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    fun setEqEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_EQ_ENABLED, value).apply()
        _eqEnabled.value = value
    }

    fun setEqLevels(value: String) {
        prefs.edit().putString(KEY_EQ_LEVELS, value).apply()
        _eqLevels.value = value
    }

    fun setNormalize(value: Boolean) {
        prefs.edit().putBoolean(KEY_NORMALIZE, value).apply()
        _normalize.value = value
    }

    fun setCrossfadeSeconds(value: Int) {
        prefs.edit().putInt(KEY_CROSSFADE, value).apply()
        _crossfadeSeconds.value = value
    }

    private val _backupFolder = MutableStateFlow(prefs.getString(KEY_BACKUP_FOLDER, null))
    /** SAF tree uri for automatic weekly backups; null = auto-backup off. */
    val backupFolder: StateFlow<String?> = _backupFolder.asStateFlow()

    fun setBackupFolder(uri: String?) {
        prefs.edit().putString(KEY_BACKUP_FOLDER, uri).apply()
        _backupFolder.value = uri
    }

    private val _releaseRadar = MutableStateFlow(prefs.getBoolean(KEY_RELEASE_RADAR, true))
    /** Notify about new albums/singles of confidants and followed artists. */
    val releaseRadar: StateFlow<Boolean> = _releaseRadar.asStateFlow()

    fun setReleaseRadar(value: Boolean) {
        prefs.edit().putBoolean(KEY_RELEASE_RADAR, value).apply()
        _releaseRadar.value = value
    }

    /** Country YouTube Music builds Home recommendations and charts for ("" = the phone's country). */
    private val _homeRegion = MutableStateFlow(prefs.getString(KEY_HOME_REGION, "JP") ?: "JP")
    val homeRegion: StateFlow<String> = _homeRegion.asStateFlow()

    fun setHomeRegion(value: String) {
        prefs.edit().putString(KEY_HOME_REGION, value).apply()
        _homeRegion.value = value
    }

    private val _radikoArea = MutableStateFlow(prefs.getString(KEY_RADIKO_AREA, "JP4") ?: "JP4")
    /** radiko area shown in the Radio tab (Sendai by default). */
    val radikoArea: StateFlow<String> = _radikoArea.asStateFlow()

    fun setRadikoArea(value: String) {
        prefs.edit().putString(KEY_RADIKO_AREA, value).apply()
        _radikoArea.value = value
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
        const val KEY_HOME_REGION = "home_region"
        const val KEY_RADIKO_AREA = "radiko_area"
        const val KEY_RELEASE_RADAR = "release_radar"
        const val KEY_BACKUP_FOLDER = "backup_folder"
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_TRANSLATE_TO = "translate_to"
        const val KEY_AUTO_DOWNLOAD_LIKED = "auto_download_liked"
        const val KEY_AUTO_DOWNLOAD_WIFI = "auto_download_wifi"
        const val KEY_EQ_LEVELS = "eq_levels"
        const val KEY_NORMALIZE = "normalize"
        const val KEY_CROSSFADE = "crossfade"
        const val KEY_DOWNLOAD_FOLDER = "download_folder"
        const val KEY_SYNC_LIKES = "sync_likes"
        const val KEY_REPORT_HISTORY = "report_history"
        const val KEY_HOME_BG = "home_background"
    }
}
