package com.example.stayfree.domain.strict

/**
 * The single source of the social networks strict mode keeps closed for every user,
 * premium or not. The only way in is a short unlock. Messaging apps are left out on
 * purpose: they are how people reach each other, not a feed.
 */
object StrictSocialApps {

    val PACKAGES: List<String> = listOf(
        "com.instagram.android",
        "com.instagram.lite",
        "com.instagram.barcelona", // Threads
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.snapchat.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.zhiliaoapp.musically.go",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.pinterest",
        "com.bereal.ft",
        "com.tumblr"
    )

    private val SET = PACKAGES.toSet()

    fun isSocial(packageName: String): Boolean = packageName in SET

    fun installed(isInstalled: (String) -> Boolean): List<String> = PACKAGES.filter(isInstalled)
}
