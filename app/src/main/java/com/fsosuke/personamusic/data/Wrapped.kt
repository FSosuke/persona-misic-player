package com.fsosuke.personamusic.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** The P5 calendar's times of day. */
enum class DayPhase(val label: String, val fromHour: Int) {
    EarlyMorning("Early Morning", 5),
    Morning("Morning", 8),
    Daytime("Daytime", 12),
    AfterSchool("After School", 15),
    Evening("Evening", 18),
    LateNight("Late Night", 22);

    companion object {
        fun of(hour: Int): DayPhase = entries.lastOrNull { hour >= it.fromHour } ?: LateNight
    }
}

/** A recap period: one month or one year. */
sealed interface WrappedPeriod {
    val title: String
    val start: LocalDate
    val endExclusive: LocalDate

    data class Month(val month: YearMonth) : WrappedPeriod {
        override val title: String get() =
            month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() } + " ${month.year}"
        override val start: LocalDate get() = month.atDay(1)
        override val endExclusive: LocalDate get() = month.plusMonths(1).atDay(1)
    }

    data class Year(val year: Int) : WrappedPeriod {
        override val title: String get() = "$year"
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val endExclusive: LocalDate get() = LocalDate.of(year + 1, 1, 1)
    }
}

data class RankedSong(val song: Song, val plays: Int)
data class RankedArtist(val confidant: Confidant, val playsInPeriod: Int)

data class WrappedData(
    val period: WrappedPeriod,
    val plays: Int,
    val listenedMs: Long,
    val distinctSongs: Int,
    val distinctArtists: Int,
    val topSongs: List<RankedSong>,
    val topArtists: List<RankedArtist>,
    val favoritePhase: DayPhase?,
    val busiestDay: LocalDate?,
    val busiestDayPlays: Int,
    val longestStreakDays: Int,
    val newArtists: List<String>,
) {
    val isEmpty: Boolean get() = plays == 0
}

/** Computes calendars and Wrapped recaps from the play history. */
class ListeningHistory(private val dao: MusicDao) {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun millis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun hourOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(zone).hour

    /** Plays per day of [month] (only days with plays). */
    fun monthStamps(month: YearMonth) = dao.playStamps(millis(month.atDay(1)), millis(month.plusMonths(1).atDay(1)))

    fun dayPlays(day: LocalDate) = dao.playsBetween(millis(day), millis(day.plusDays(1)))

    suspend fun firstPlayDate(): LocalDate? = dao.firstPlayAt()?.let { dateOf(it) }

    suspend fun wrapped(period: WrappedPeriod): WrappedData {
        val plays = dao.playsBetweenNow(millis(period.start), millis(period.endExclusive))
        val songCounts = plays.groupingBy { it.song.id }.eachCount()
        val songsById = plays.associate { it.song.id to it.song }
        val topSongs = songCounts.entries.sortedByDescending { it.value }.take(5)
            .map { RankedSong(songsById.getValue(it.key), it.value) }

        val artistCounts = plays.map { primaryArtist(it.song.artist) }.filter { isRealArtist(it) }.groupingBy { it }.eachCount()
        val allTime = Confidants.fromCounts(dao.artistPlayCountsNow()).associateBy { it.artist }
        val topArtists = artistCounts.entries.sortedByDescending { it.value }.take(5).map { (artist, count) ->
            RankedArtist(allTime[artist] ?: Confidants.of(artist, count), count)
        }

        val phase = plays.groupingBy { DayPhase.of(hourOf(it.playedAt)) }.eachCount().maxByOrNull { it.value }?.key
        val perDay = plays.groupingBy { dateOf(it.playedAt) }.eachCount()
        val busiest = perDay.maxByOrNull { it.value }

        var longest = 0
        var current = 0
        var previous: LocalDate? = null
        for (day in perDay.keys.sorted()) {
            current = if (previous != null && previous.plusDays(1) == day) current + 1 else 1
            longest = maxOf(longest, current)
            previous = day
        }

        val from = millis(period.start)
        val to = millis(period.endExclusive)
        val firstPlays = dao.artistFirstPlays()
            .groupBy({ primaryArtist(it.name) }, { it.firstPlayed })
            .mapValues { (_, times) -> times.min() }
        val newArtists = firstPlays.filter { (artist, first) -> isRealArtist(artist) && first in from until to }
            .entries.sortedBy { it.value }.map { it.key }

        return WrappedData(
            period = period,
            plays = plays.size,
            listenedMs = plays.sumOf { it.listenedMs },
            distinctSongs = songCounts.size,
            distinctArtists = artistCounts.size,
            topSongs = topSongs,
            topArtists = topArtists,
            favoritePhase = phase,
            busiestDay = busiest?.key,
            busiestDayPlays = busiest?.value ?: 0,
            longestStreakDays = longest,
            newArtists = newArtists,
        )
    }
}
