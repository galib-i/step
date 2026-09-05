package com.galib.step.util

import java.util.Locale

object Formatters {

    fun steps(steps: Long): String = String.format(Locale.getDefault(), "%,d", steps)

    fun compactSteps(steps: Long): String = when {
        steps >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", steps / 1_000_000.0)
        steps >= 1_000 -> String.format(Locale.getDefault(), "%.1fk", steps / 1000.0)
        else -> steps.toString()
    }
}
