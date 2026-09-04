package com.galib.step.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/*
 * ColorBlendr-style palette treatments applied on top of any scheme
 * (static palette or Material You dynamic): saturation/hue remaps of the
 * accent roles, full desaturation for monochrome, plus a pure-black AMOLED
 * surface ladder.
 */

/** Pure-black window with a near-black elevation ladder for AMOLED panels. */
fun ColorScheme.applyAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF222222),
    surfaceVariant = Color(0xFF1C1C1C)
)
