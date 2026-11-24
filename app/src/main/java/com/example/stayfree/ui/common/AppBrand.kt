package com.example.stayfree.ui.common

/** Fallback look for an app tile whose real icon can't be loaded. */
object AppBrand {

    const val DEFAULT_COLOR: Int = 0xFF262626.toInt()

    private val colors = mapOf(
        "com.instagram.android" to 0xFFC13584.toInt(),
        "com.google.android.youtube" to 0xFFE02424.toInt(),
        "com.zhiliaoapp.musically" to 0xFF2B2B2B.toInt(),
        "com.ss.android.ugc.trill" to 0xFF2B2B2B.toInt(),
        "com.android.chrome" to 0xFF1A73E8.toInt(),
    )

    fun colorFor(packageName: String): Int = colors[packageName] ?: DEFAULT_COLOR

    fun letterFor(label: String): String =
        label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
}
