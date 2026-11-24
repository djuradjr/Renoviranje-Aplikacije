package com.example.stayfree.util

import androidx.appcompat.app.AppCompatDelegate

object AppearanceModes {
    /** D4 is dark-only. The light palette in values/ only stays until removing it is approved. */
    @Suppress("UNUSED_PARAMETER")
    fun toNightMode(mode: String): Int = AppCompatDelegate.MODE_NIGHT_YES
}
