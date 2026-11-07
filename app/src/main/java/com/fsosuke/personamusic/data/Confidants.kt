package com.fsosuke.personamusic.data

/** The 22 Major Arcana, as used for Persona confidants. */
enum class Arcana(val numeral: String, val title: String) {
    Fool("0", "Fool"),
    Magician("I", "Magician"),
    Priestess("II", "Priestess"),
    Empress("III", "Empress"),
    Emperor("IV", "Emperor"),
    Hierophant("V", "Hierophant"),
    Lovers("VI", "Lovers"),
    Chariot("VII", "Chariot"),
    Justice("VIII", "Justice"),
    Hermit("IX", "Hermit"),
    Fortune("X", "Fortune"),
    Strength("XI", "Strength"),
    HangedMan("XII", "Hanged Man"),
    Death("XIII", "Death"),
    Temperance("XIV", "Temperance"),
    Devil("XV", "Devil"),
    Tower("XVI", "Tower"),
    Star("XVII", "Star"),
    Moon("XVIII", "Moon"),
    Sun("XIX", "Sun"),
    Judgement("XX", "Judgement"),
    World("XXI", "World"),
}

/** An artist you listen to, seen as a Persona confidant. */
data class Confidant(
    val artist: String,
    val plays: Int,
    val rank: Int,
    val arcana: Arcana,
    /** 0..1 progress towards the next rank (1 at MAX). */
    val progress: Float,
    val playsToNext: Int?,
) {
    val isMax: Boolean get() = rank == Confidants.MAX_RANK
}

data class RankUp(val confidant: Confidant, val previousRank: Int)

object Confidants {
    const val MAX_RANK = 10

    /** Plays needed to reach rank 1..10: early ranks come quickly, MAX takes dedication. */
    private val thresholds = intArrayOf(1, 3, 6, 10, 16, 24, 35, 50, 70, 100)

    fun rankFor(plays: Int): Int = thresholds.count { plays >= it }.coerceIn(0, MAX_RANK)

    /** Every artist keeps the same arcana forever, derived from their name. */
    fun arcanaFor(artist: String): Arcana {
        val hash = artist.lowercase().fold(7) { acc, c -> acc * 31 + c.code }
        return Arcana.entries[Math.floorMod(hash, Arcana.entries.size)]
    }

    fun of(artist: String, plays: Int): Confidant {
        val rank = rankFor(plays)
        val (progress, toNext) = if (rank >= MAX_RANK) {
            1f to null
        } else {
            val from = if (rank == 0) 0 else thresholds[rank - 1]
            val to = thresholds[rank]
            ((plays - from).toFloat() / (to - from)).coerceIn(0f, 1f) to (to - plays)
        }
        return Confidant(artist, plays, rank, arcanaFor(artist), progress, toNext)
    }

    /** Folds per-artist-string play counts ("A, B" counts for A) into confidants. */
    fun fromCounts(counts: List<ArtistPlays>): List<Confidant> = counts
        .groupBy({ primaryArtist(it.name) }, { it.plays })
        .filterKeys { isRealArtist(it) }
        .map { (artist, plays) -> of(artist, plays.sum()) }
        .filter { it.rank > 0 }
        .sortedWith(compareByDescending<Confidant> { it.plays }.thenBy { it.artist.lowercase() })
}
