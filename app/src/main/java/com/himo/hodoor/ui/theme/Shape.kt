package com.himo.hodoor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Consistent spacing scale used across screens. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Shared corner radii: 16dp cards, 28dp buttons/dialogs, 12dp fields. */
object HodoorShapes {
    val card = RoundedCornerShape(16.dp)
    val button = RoundedCornerShape(28.dp)
    val field = RoundedCornerShape(12.dp)
    val dialog = RoundedCornerShape(28.dp)

    val material = Shapes(
        extraSmall = field,
        small = field,
        medium = card,
        large = card,
        extraLarge = dialog
    )
}
