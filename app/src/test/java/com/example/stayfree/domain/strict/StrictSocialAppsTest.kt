package com.example.stayfree.domain.strict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictSocialAppsTest {

    @Test
    fun coversTheBigFeeds() {
        listOf(
            "com.instagram.android", "com.google.android.youtube", "com.facebook.katana",
            "com.snapchat.android", "com.zhiliaoapp.musically", "com.twitter.android"
        ).forEach { assertTrue(it, StrictSocialApps.isSocial(it)) }
    }

    @Test
    fun leavesMessagingAppsOpen() {
        listOf(
            "com.whatsapp", "com.facebook.orca", "org.telegram.messenger",
            "com.discord", "com.viber.voip"
        ).forEach { assertFalse(it, StrictSocialApps.isSocial(it)) }
    }

    @Test
    fun installedKeepsListOrderAndOnlyInstalled() {
        val onPhone = setOf("com.tumblr", "com.instagram.android", "com.whatsapp")
        assertEquals(listOf("com.instagram.android", "com.tumblr"), StrictSocialApps.installed { it in onPhone })
    }

    @Test
    fun listHasNoDuplicates() {
        assertEquals(StrictSocialApps.PACKAGES.size, StrictSocialApps.PACKAGES.toSet().size)
    }
}
