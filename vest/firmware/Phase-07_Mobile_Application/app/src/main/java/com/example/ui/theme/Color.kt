package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================================
//  SafeGuard "Liquid Glass" palette
//  Light : soft off-white page, white frosted-glass cards, blue accent (like the dock reference)
//  Dark  : near-black page with a faint violet tint, smoky frosted-glass cards, brighter blue accent
//  NOTE: the constant NAMES are kept from the old theme so every screen keeps compiling and
//  `background == CommandNavyDark` still works as the "is dark" test. Only the VALUES changed.
// ============================================================================================

// ---- Dark theme ----
val CommandNavyDark = Color(0xFF0B0B10)            // page background
val CommandSurfaceDark = Color(0xFF17171D)
val CommandSurfaceVariantDark = Color(0xFF1F1F27)
val CommandSurfaceContainerDark = Color(0xFF25252E)
val CommandBorderDark = Color(0xFF34343F)

// ---- Accents (dark mode) ----
// "CyanNeon" is the main accent of the dark theme. It is now the Liquid-Glass blue.
// (Humidity keeps its own cyan #00E5FF - hard-coded in the chart/sensor code, per AGENTS.md.)
val CyanNeon = Color(0xFF5B9BFF)
val CyanNeonGlow = Color(0x335B9BFF)
val TealAccent = Color(0xFF7FB2FF)
val SafetyAmber = Color(0xFFFFA43A)
val HazardCrimson = Color(0xFFFF4D5E)
val EmeraldOnline = Color(0xFF34D399)
val OfflineRed = Color(0xFFFF5C5C)

// ---- Light theme ----
val CommandLightBg = Color(0xFFF4F1F7)             // exactly the reference page colour
val CommandLightSurface = Color(0xFFFFFFFF)
val CommandLightSurfaceVariant = Color(0xFFEBE8F0)
val CommandLightBorder = Color(0xFFDAD6E2)
val CommandLightPrimary = Color(0xFF2F6FD6)        // reference dock accent blue
val CommandLightSecondary = Color(0xFFC2650A)
val CommandLightEmerald = Color(0xFF059669)
val CommandLightRed = Color(0xFFDC2626)
val CommandLightTeal = Color(0xFF3B82F6)

// ---- Text ----
val TextWhite = Color(0xFFF5F5F8)
val TextMuted = Color(0xFF9A9AAB)
val TextDark = Color(0xFF111118)
val TextDarkMuted = Color(0xFF5B5B6B)
