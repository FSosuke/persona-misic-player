package com.fsosuke.personamusic.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The arcana a listening profile can fall under, with Igor's reading of it. */
enum class ListeningArcana(val numeral: String, val title: String, val japanese: String, val reading: String) {
    Fool("0", "The Fool", "愚者", "A traveller with no map. You keep walking into music you've never heard – every week brings strangers who become companions."),
    Magician("I", "The Magician", "魔術師", "Many voices answer your call. You weave dozens of artists together and none of them holds you for long."),
    Priestess("II", "The Priestess", "女教皇", "You keep to one language of sound. Your world is deep rather than wide, and you know its corners by heart."),
    Lovers("VI", "The Lovers", "恋愛", "Your heart belongs to a chosen few. One name returns again and again – a bond, not a habit."),
    Chariot("VII", "The Chariot", "戦車", "Restless and driven. You skip ahead until a song earns its place – only the strongest survive your queue."),
    Hermit("IX", "The Hermit", "隠者", "You return to the same rooms. A handful of songs on repeat is your sanctuary."),
    Star("XVII", "The Star", "星", "You collect what moves you. Your hearts mark a path of songs worth keeping."),
    Moon("XVIII", "The Moon", "月", "Your music lives after dark. When the city sleeps, your playlist wakes."),
    Sun("XIX", "The Sun", "太陽", "You greet the day with sound. Mornings are when your music shines brightest."),
    Fortune("X", "Fortune", "運命", "No single force rules you. Your listening turns like a wheel – balanced, changing, hard to predict."),
}

data class Trait(val label: String, val japanese: String, val value: Float)

data class ListeningReading(
    val arcana: ListeningArcana,
    val runnerUp: ListeningArcana?,
    val traits: List<Trait>,
    val patterns: List<String>,
    /** Plain facts for "Igor speaks" (no personal data beyond the music). */
    val facts: List<String>,
    val plays: Int,
)

/**
 * Reads a listening profile from the play log – rule-based and on the device. Needs some
 * history; returns null below [MIN_PLAYS] plays.
 */
object VelvetRoom {
    const val MIN_PLAYS = 20

    fun read(
        plays: List<PlayRecord>,
        likedCount: Int,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ListeningReading? {
        if (plays.size < MIN_PLAYS) return null
        val n = plays.size.toFloat()
        val times = plays.map { Instant.ofEpochMilli(it.playedAt).atZone(zone) }
        val artists = plays.map { primaryArtist(it.song.artist) }
        val artistCounts = artists.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        val songCounts = plays.groupingBy { it.song.id }.eachCount().entries.sortedByDescending { it.value }

        val distinctSongs = songCounts.size
        val distinctArtists = artistCounts.size
        val loyalty = artistCounts.first().value / n
        val repeat = n / distinctSongs
        val night = times.count { it.hour >= 22 || it.hour < 5 } / n
        val morning = times.count { it.hour in 5..9 } / n
        val japanese = plays.count { ContentFilter.isJapanese(it.song.title) || ContentFilter.isJapanese(it.song.artist) } / n
        val judged = plays.filter { it.listenedMs > 0 && it.song.durationMs > 60_000 }
        val skip = if (judged.isEmpty()) 0f else judged.count { it.listenedMs < 30_000 } / judged.size.toFloat()
        val monthAgo = now - 30L * 24 * 60 * 60 * 1000
        val firstPlay = HashMap<String, Long>()
        plays.forEachIndexed { i, p -> firstPlay.putIfAbsent(artists[i], p.playedAt) }
        val recentArtists = plays.indices.filter { plays[it].playedAt >= monthAgo }.map { artists[it] }.toSet()
        val newArtists = recentArtists.filter { (firstPlay[it] ?: 0) >= monthAgo }
        val discovery = if (recentArtists.isEmpty()) 0f else newArtists.size / recentArtists.size.toFloat()
        val diversity = distinctArtists / n
        val likeRate = (likedCount / distinctSongs.toFloat()).coerceAtMost(1f)

        // How strongly each arcana fits, roughly 0..1.
        val scores = mapOf(
            ListeningArcana.Fool to discovery * 1.1f,
            ListeningArcana.Magician to diversity * 1.4f,
            ListeningArcana.Priestess to if (japanese > 0.85f) japanese * 0.9f else 0f,
            ListeningArcana.Lovers to loyalty * 2.2f,
            ListeningArcana.Chariot to skip * 1.8f,
            ListeningArcana.Hermit to ((repeat - 1f) / 5f).coerceIn(0f, 1f),
            ListeningArcana.Star to likeRate * 1.2f,
            ListeningArcana.Moon to night * 1.9f,
            ListeningArcana.Sun to morning * 2.2f,
            ListeningArcana.Fortune to 0.35f,
        ).entries.sortedByDescending { it.value }

        val traits = listOf(
            Trait("Explorer", "探求", discovery),
            Trait("Loyalty", "忠誠", (loyalty * 3).coerceAtMost(1f)),
            Trait("Night owl", "夜型", (night * 2).coerceAtMost(1f)),
            Trait("On repeat", "反復", ((repeat - 1f) / 4f).coerceIn(0f, 1f)),
            Trait("Japanese", "日本", japanese),
        )

        val peakHour = times.groupingBy { it.hour }.eachCount().maxByOrNull { it.value }!!.key
        val peakDay = times.groupingBy { it.dayOfWeek }.eachCount().maxByOrNull { it.value }!!.key
        val topArtist = artistCounts.first()
        val topSong = plays.first { it.song.id == songCounts.first().key }.song
        val patterns = buildList {
            add("You listen most on ${dayName(peakDay)}s around ${"%02d".format(peakHour)}:00.")
            add("${topArtist.key} makes up ${pct(loyalty)} of your plays.")
            add("\"${topSong.title}\" by ${topSong.artist} ran ${songCounts.first().value} times.")
            if (newArtists.isNotEmpty()) add("${newArtists.size} new artists entered your life this month – like ${newArtists.take(3).joinToString(", ")}.")
            add("${pct(japanese)} of what you hear is Japanese.")
            if (judged.size >= 20) add("You skip ${pct(skip)} of songs within 30 seconds.")
        }
        val facts = buildList {
            add("Plays analysed: ${plays.size} (last 90 days), ${distinctSongs} songs, $distinctArtists artists")
            add("Top artists: " + artistCounts.take(8).joinToString { "${it.key} (${it.value})" })
            add("Most played songs: " + songCounts.take(6).joinToString { e ->
                plays.first { it.song.id == e.key }.song.let { "${it.title} – ${it.artist} (${e.value})" }
            })
            add("New artists this month: " + newArtists.take(8).joinToString().ifEmpty { "none" })
            add("Peak listening: ${dayName(peakDay)} around $peakHour:00; night share ${pct(night)}, morning share ${pct(morning)}")
            add("Japanese share ${pct(japanese)}, skip rate ${pct(skip)}, songs liked $likedCount")
            add("ListeningArcana by rules: ${scores[0].key.title}, runner-up ${scores[1].key.title}")
        }
        return ListeningReading(scores[0].key, scores[1].key.takeIf { scores[1].value > 0.3f }, traits, patterns, facts, plays.size)
    }

    private fun pct(value: Float) = "${(value * 100).roundToInt()}%"
    private fun dayName(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
}
