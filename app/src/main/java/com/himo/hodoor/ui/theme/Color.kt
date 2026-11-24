package com.himo.hodoor.ui.theme

import androidx.compose.ui.graphics.Color

/** Palette derived from the graduation-cap logo. */
val LogoDeepPurple = Color(0xFF150340)
val BrandPrimary = Color(0xFF8B4DFF)
val OffWhite = Color(0xFFFCFAFF)

// Material 3 light scheme roles.
val LightPrimary = BrandPrimary
val LightOnPrimary = Color.White
val LightPrimaryContainer = Color(0xFFE8DBFF)
val LightOnPrimaryContainer = LogoDeepPurple
val LightSecondary = LogoDeepPurple
val LightOnSecondary = OffWhite
val LightSecondaryContainer = Color(0xFFE6DFF5)
val LightOnSecondaryContainer = LogoDeepPurple
val LightTertiary = Color(0xFF0F766E)
val LightOnTertiary = Color.White
val LightTertiaryContainer = Color(0xFFC8F2EC)
val LightOnTertiaryContainer = Color(0xFF003B36)
val LightBackground = OffWhite
val LightOnBackground = LogoDeepPurple
val LightSurface = Color.White
val LightOnSurface = LogoDeepPurple
val LightSurfaceVariant = Color(0xFFF1ECFB)
val LightOnSurfaceVariant = Color(0xFF4A3A6B)
val LightOutline = Color(0xFF7A6A9C)
val LightOutlineVariant = Color(0xFFD8CFEA)
val LightError = Color(0xFFC62828)
val LightOnError = Color.White
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

// Material 3 dark scheme roles.
val DarkPrimary = Color(0xFFB69AFF)
val DarkOnPrimary = LogoDeepPurple
val DarkPrimaryContainer = Color(0xFF3A1F6B)
val DarkOnPrimaryContainer = Color(0xFFEADDFF)
val DarkSecondary = Color(0xFF9E86D6)
val DarkOnSecondary = LogoDeepPurple
val DarkSecondaryContainer = Color(0xFF2E214B)
val DarkOnSecondaryContainer = Color(0xFFEADDFF)
val DarkTertiary = Color(0xFF5EEAD4)
val DarkOnTertiary = Color(0xFF00201B)
val DarkTertiaryContainer = Color(0xFF134E4A)
val DarkOnTertiaryContainer = Color(0xFFC8F2EC)
val DarkBackground = Color(0xFF0F0A1F)
val DarkOnBackground = Color(0xFFF3EDFF)
val DarkSurface = Color(0xFF1A1230)
val DarkOnSurface = Color(0xFFF3EDFF)
val DarkSurfaceVariant = Color(0xFF2A2042)
val DarkOnSurfaceVariant = Color(0xFFC9BDE6)
val DarkOutline = Color(0xFF8E7DB0)
val DarkOutlineVariant = Color(0xFF44376A)
val DarkError = Color(0xFFFF8A80)
val DarkOnError = Color(0xFF410002)
val DarkErrorContainer = Color(0xFF5C1F1F)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

/** Semantic colors used by late / absence / cause UI, in one place. */
data class HodoorColors(
    val late: Color,
    val lateContainer: Color,
    val absent: Color,
    val absentContainer: Color,
    val cause: Color,
    val causeContainer: Color
)

val LightHodoorColors = HodoorColors(
    late = Color(0xFFB26A00),
    lateContainer = Color(0xFFFFF3D6),
    absent = Color(0xFFC62828),
    absentContainer = Color(0xFFFFE2E0),
    cause = Color(0xFF0F766E),
    causeContainer = Color(0xFFD6F5F0)
)

val DarkHodoorColors = HodoorColors(
    late = Color(0xFFFFC65C),
    lateContainer = Color(0xFF4A3300),
    absent = Color(0xFFFF8A80),
    absentContainer = Color(0xFF5C1F1F),
    cause = Color(0xFF5EEAD4),
    causeContainer = Color(0xFF134E4A)
)
