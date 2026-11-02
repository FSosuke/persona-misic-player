package com.fsosuke.personamusic.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.fsosuke.personamusic.R
import com.fsosuke.personamusic.data.Arcana
import com.fsosuke.personamusic.data.ListeningArcana

/**
 * Which look the app wears: Persona 5 (default) or the Kamen Rider Zero-One skin picked under
 * Customize > Theme. Snapshot state, so everything reading it redraws when the theme changes.
 */
object Skin {
    var zeroOne by mutableStateOf(false)
}

/** Free (OFL) Saira faces for the Zero-One skin: a stencil for headlines, condensed for text. */
object ZeroOneFonts {
    val stencil = FontFamily(Font(R.font.z1_stencil))
    val saira = FontFamily(
        Font(R.font.z1_saira_medium, FontWeight.Medium),
        Font(R.font.z1_saira_medium, FontWeight.Normal),
        Font(R.font.z1_saira_bold, FontWeight.Bold),
        Font(R.font.z1_saira_bold, FontWeight.SemiBold),
        Font(R.font.z1_saira_black, FontWeight.Black),
        Font(R.font.z1_saira_black, FontWeight.ExtraBold),
    )
}

/**
 * Zero-One names for Persona terms ("Confidants" → "Progrise Keys"). Unknown text stays as it is;
 * lookup ignores case.
 */
object Lingo {
    private val zeroOne = mapOf(
        "confidants" to "Progrise Keys",
        "confidant" to "Progrise Key",
        "velvet room" to "Zea Analysis",
        "igor's picks" to "Zea's picks",
        "igor speaks" to "Zea speaks",
        "social stats" to "Rider Specs",
        "★ social stats" to "★ Rider specs",
        "🦋 velvet room" to "🛰 Zea analysis",
        "wrapped" to "Mission Report",
        "calendar" to "Schedule",
        "📅 calendar" to "📅 Schedule",
        "rank up" to "Level up",
        "rank up!" to "Level up!",
        "rank max" to "Lv. MAX",
        "take your time" to "Standby",
        "your arcana" to "Your Progrise Key",
        "traits" to "Specs",
        "patterns" to "Learning data",
        "nothing yet" to "No data yet",
        "silence…" to "Standby…",
    )

    fun of(text: String): String = if (!Skin.zeroOne) text else zeroOne[text.lowercase()] ?: text
}

/** Progrise Keys standing in for the 22 confidant arcana in the Zero-One skin. */
private val progriseKeys = listOf(
    "Rising Hopper", "Biting Shark", "Flying Falcon", "Flaming Tiger", "Freezing Bear", "Breaking Mammoth",
    "Lightning Hornet", "Sting Scorpion", "Amazing Caucasus", "Shining Hopper", "Rushing Cheetah", "Exciting Stag",
    "Trapping Spider", "Storming Penguin", "Crushing Buffalo", "Gatling Hedgehog", "Punching Kong", "Splashing Whale",
    "Shooting Wolf", "Hopping Kangaroo", "Amazing Hercules", "Metal Cluster Hopper",
)

/** The arcana's name, or its Progrise Key in the Zero-One skin. */
val Arcana.displayName: String get() = if (Skin.zeroOne) progriseKeys[ordinal % progriseKeys.size] else title

/** Listening "arcana" in the Velvet Room, as a Progrise Key in the Zero-One skin. */
val ListeningArcana.displayName: String
    get() = if (!Skin.zeroOne) title else when (this) {
        ListeningArcana.Fool -> "Flying Falcon"
        ListeningArcana.Magician -> "Shining Assault Hopper"
        ListeningArcana.Priestess -> "Breaking Mammoth"
        ListeningArcana.Lovers -> "Rising Hopper"
        ListeningArcana.Chariot -> "Lightning Hornet"
        ListeningArcana.Hermit -> "Freezing Bear"
        ListeningArcana.Star -> "Shining Hopper"
        ListeningArcana.Moon -> "Sting Scorpion"
        ListeningArcana.Sun -> "Flaming Tiger"
        ListeningArcana.Fortune -> "Biting Shark"
    }

/** "Rank" in Persona, "Lv." for Progrise Keys. */
fun rankLabel(rank: Int, max: Boolean = false): String = when {
    Skin.zeroOne && max -> "LV. MAX"
    Skin.zeroOne -> "Lv. $rank"
    max -> "RANK MAX"
    else -> "Rank $rank"
}
