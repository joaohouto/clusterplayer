package com.joaohouto.clusterplayer.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.joaohouto.clusterplayer.R

data class ClusterAccent(
    val id: String,
    @param:StringRes val nameRes: Int,
    val primary: Color,
    val dark: Color,
    val glow: Color
)

val AccentNeedleRed = ClusterAccent(
    id = "needle_red",
    nameRes = R.string.theme_needle_red,
    primary = Color(0xFFE61924),
    dark = Color(0xFFB0121B),
    glow = Color(0x33E61924)
)

val AccentMSportBlue = ClusterAccent(
    id = "m_sport_blue",
    nameRes = R.string.theme_m_sport_blue,
    primary = Color(0xFF0088FF),
    dark = Color(0xFF0055B3),
    glow = Color(0x330088FF)
)

val AccentRacingYellow = ClusterAccent(
    id = "racing_yellow",
    nameRes = R.string.theme_racing_yellow,
    primary = Color(0xFFFFCC00),
    dark = Color(0xFFB38F00),
    glow = Color(0x33FFCC00)
)

val AccentGreenHell = ClusterAccent(
    id = "green_hell",
    nameRes = R.string.theme_green_hell,
    primary = Color(0xFF00E676),
    dark = Color(0xFF00964D),
    glow = Color(0x3300E676)
)

val AccentSunsetOrange = ClusterAccent(
    id = "sunset_orange",
    nameRes = R.string.theme_sunset_orange,
    primary = Color(0xFFFF6D00),
    dark = Color(0xFFB84E00),
    glow = Color(0x33FF6D00)
)

val AccentElectricCyan = ClusterAccent(
    id = "electric_cyan",
    nameRes = R.string.theme_electric_cyan,
    primary = Color(0xFF00E5FF),
    dark = Color(0xFF009EB0),
    glow = Color(0x3300E5FF)
)

val AccentPureSilver = ClusterAccent(
    id = "pure_silver",
    nameRes = R.string.theme_pure_silver,
    primary = Color(0xFFE2E8F0),
    dark = Color(0xFF94A3B8),
    glow = Color(0x33E2E8F0)
)

val ALL_ACCENT_THEMES = listOf(
    AccentNeedleRed,
    AccentMSportBlue,
    AccentRacingYellow,
    AccentGreenHell,
    AccentSunsetOrange,
    AccentElectricCyan,
    AccentPureSilver
)

fun getAccentThemeById(id: String?): ClusterAccent {
    return ALL_ACCENT_THEMES.find { it.id.equals(id, ignoreCase = true) } ?: AccentNeedleRed
}

val LocalClusterAccent = compositionLocalOf { AccentNeedleRed }
