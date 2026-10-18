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
    }
}
