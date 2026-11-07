package com.fsosuke.personamusic.data

private val separators = Regex("\\s*(,|&|\\bfeat\\.?|\\bft\\.?|\\bx\\b|/)\\s*", RegexOption.IGNORE_CASE)

/** "Lyn, ATLUS Sound Team" → "Lyn". */
fun primaryArtist(artist: String): String = artist.split(separators).firstOrNull { it.isNotBlank() }?.trim() ?: artist

fun isRealArtist(artist: String) = artist.isNotBlank() && !artist.startsWith("Unknown", ignoreCase = true)
