package com.fsosuke.personamusic.velvet

import android.content.Context
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.fsosuke.personamusic.data.ListeningReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** What Igor said, plus the songs he named (to look up on YouTube Music). */
data class IgorSpeech(val text: String, val suggestions: List<String>)

/**
 * "Igor speaks": turns the on-device reading into a personal Persona-style reading with Claude.
 * Optional – needs the user's own Anthropic API key; only aggregated music stats are sent.
 */
class IgorNarrator(context: Context) {
    private val prefs = context.getSharedPreferences("velvet_room", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API, "").orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _last = MutableStateFlow(
        prefs.getString(KEY_LAST, null)?.let { IgorSpeech(it, prefs.getString(KEY_SUGGESTIONS, "").orEmpty().lines().filter(String::isNotBlank)) },
    )
    val last: StateFlow<IgorSpeech?> = _last.asStateFlow()

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API, key.trim()).apply()
        _apiKey.value = key.trim()
    }

    suspend fun speak(reading: ListeningReading): Result<IgorSpeech> = withContext(Dispatchers.IO) {
        val key = _apiKey.value
        if (key.isBlank()) return@withContext Result.failure(IllegalStateException("Add your Anthropic API key first."))
        val client = AnthropicOkHttpClient.builder().apiKey(key).build()
        try {
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(4000L)
                // A short creative text – low effort keeps it quick and cheap.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .system(SYSTEM)
                .addUserMessage(prompt(reading))
                // If a safety check declines the request, let the API retry on a suitable model.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build()
            val message = client.messages().create(params)
            if (message.stopReason().map { it.asString() }.orElse(null) == "refusal") {
                return@withContext Result.failure(IllegalStateException("Igor stayed silent this time. Try again later."))
            }
            val text = message.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("").trim()
            val speech = parse(text)
            prefs.edit().putString(KEY_LAST, speech.text).putString(KEY_SUGGESTIONS, speech.suggestions.joinToString("\n")).apply()
            _last.value = speech
            Result.success(speech)
        } catch (e: UnauthorizedException) {
            Result.failure(IllegalStateException("The API key was rejected – check it in the Velvet Room settings."))
        } catch (e: PermissionDeniedException) {
            Result.failure(IllegalStateException("This API key isn't allowed to use $MODEL."))
        } catch (e: RateLimitException) {
            Result.failure(IllegalStateException("Too many requests right now – try again in a minute."))
        } catch (e: AnthropicServiceException) {
            Result.failure(IllegalStateException("Claude answered with an error (${e.statusCode()})."))
        } catch (e: AnthropicIoException) {
            Result.failure(IllegalStateException("No connection to Claude."))
        } finally {
            client.close()
        }
    }

    private fun prompt(reading: ListeningReading) = buildString {
        appendLine("Here is my music listening data from my phone's player app:")
        reading.facts.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Give me my reading.")
    }

    /** Splits off the "PICKS:" list at the end. */
    private fun parse(text: String): IgorSpeech {
        val marker = text.lastIndexOf("PICKS:")
        if (marker < 0) return IgorSpeech(text, emptyList())
        val picks = text.substring(marker + 6).lines()
            .map { it.trim().removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotBlank() }
            .take(8)
        return IgorSpeech(text.substring(0, marker).trim(), picks)
    }

    companion object {
        const val MODEL = "claude-opus-5-5"
        private const val KEY_API = "anthropic_api_key"
        private const val KEY_LAST = "last_speech"
        private const val KEY_SUGGESTIONS = "last_suggestions"

        private val SYSTEM = """
            You are Igor, master of the Velvet Room from Persona 5, giving a music fan a reading of their listening habits.
            Speak as Igor: courteous, mysterious, theatrical, addressing the listener as "my dear guest", and use a
            tarot arcana as the frame (you may confirm or overrule the arcana suggested by the data). Be specific – name
            artists and songs from the data and point out real patterns (times of day, loyalty, discovery, repeats).
            Write in English, 150-220 words, plain text without markdown or headings. You may sprinkle one or two
            Japanese words.
            After the reading, end with a line "PICKS:" followed by 5 songs the listener has probably not heard yet but
            would likely love, one per line as "Artist - Title". Choose real, existing songs that fit their taste, and
            avoid artists and songs already listed in the data.
        """.trimIndent()
    }
}
