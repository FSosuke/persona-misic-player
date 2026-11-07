package com.fsosuke.personamusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Persona 5's five social stats, earned by how you listen. */
enum class SocialStat(
    val title: String,
    val japanese: String,
    val levels: List<String>,
    /** Value needed for level 2..5 (level 1 is free). */
    val thresholds: List<Int>,
    val unit: String,
    val howTo: String,
) {
    Knowledge(
        "Knowledge", "知識",
        listOf("Oblivious", "Learned", "Scholarly", "Encyclopedic", "Erudite"),
        listOf(5, 15, 35, 70), "artists discovered",
        "Grows with every new artist you listen to.",
    ),
    Guts(
        "Guts", "度胸",
        listOf("Milquetoast", "Bold", "Staunch", "Dauntless", "Lionhearted"),
        listOf(10, 40, 100, 250), "late-night plays",
        "Listen between 22:00 and 5:00 – the Metaverse never sleeps.",
    ),
    Proficiency(
        "Proficiency", "器用さ",
        listOf("Bumbling", "Decent", "Skilled", "Masterful", "Transcendent"),
        listOf(3, 7, 14, 30), "days in a row (best streak)",
        "Keep a daily listening streak going.",
    ),
    Kindness(
        "Kindness", "優しさ",
        listOf("Inoffensive", "Considerate", "Empathetic", "Selfless", "Angelic"),
        listOf(5, 20, 50, 120), "songs hearted",
        "Show some love: heart the songs you like.",
    ),
    Charm(
        "Charm", "魅力",
        listOf("Existent", "Head-Turning", "Suave", "Charismatic", "Debonair"),
        listOf(10, 30, 75, 150), "playlist picks",
        "Curate: every song you add to a playlist counts (a new playlist counts 5).",
    ),
}

data class StatValue(val stat: SocialStat, val value: Int) {
    /** 1..5 */
    val level: Int get() = 1 + stat.thresholds.count { value >= it }
    val levelTitle: String get() = stat.levels[level - 1]
    val isMax: Boolean get() = level == 5
    val nextAt: Int? get() = stat.thresholds.getOrNull(level - 1)
    /** 0..1 towards the next level. */
    val progress: Float get() {
        val next = nextAt ?: return 1f
        val from = if (level == 1) 0 else stat.thresholds[level - 2]
        return ((value - from).toFloat() / (next - from)).coerceIn(0f, 1f)
    }
}

data class StatUp(val stat: StatValue, val previousLevel: Int)

/** Computes the stats and remembers levels to detect "stat up!" moments. */
class SocialStats(context: Context, private val dao: MusicDao) {
    private val prefs = context.getSharedPreferences("social_stats", Context.MODE_PRIVATE)

    suspend fun compute(): List<StatValue> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val times = dao.allPlayTimes()
        val lateNight = times.count { val h = Instant.ofEpochMilli(it).atZone(zone).hour; h >= 22 || h < 5 }
        val days = times.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSortedSet()
        var best = 0
        var run = 0
        var previous: java.time.LocalDate? = null
        for (day in days) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        val artists = Confidants.fromCounts(dao.artistPlayCountsNow()).size
        listOf(
            StatValue(SocialStat.Knowledge, artists),
            StatValue(SocialStat.Guts, lateNight),
            StatValue(SocialStat.Proficiency, best),
            StatValue(SocialStat.Kindness, dao.likedCount()),
            StatValue(SocialStat.Charm, dao.playlistEntryCount() + dao.playlistCount() * 5),
        )
    }

    /** Compares with the last known levels; returns the stats that went up (and stores the new levels). */
    suspend fun checkForStatUps(): List<StatUp> {
        val values = compute()
        val firstRun = !prefs.contains("initialized")
        val ups = values.mapNotNull { v ->
            val before = prefs.getInt(v.stat.name, v.level)
            if (!firstRun && v.level > before) StatUp(v, before) else null
        }
        prefs.edit().apply {
            putBoolean("initialized", true)
            values.forEach { putInt(it.stat.name, it.level) }
        }.apply()
        return ups
    }
}
