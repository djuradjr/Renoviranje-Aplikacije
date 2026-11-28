package com.example.stayfree.domain.cleanfeed

import com.example.stayfree.domain.cleanfeed.CleanNetwork.Nav
import org.junit.Assert.assertEquals
import org.junit.Test

/** Path rules only: decide() needs android.net.Uri, which plain unit tests lack. */
class YouTubeNetworkTest {

    private val home = Nav.Redirect(YouTubeNetwork.HOME_URL)

    private fun postId(path: String, vararg query: Pair<String, String>): String? =
        YouTubeNetwork.postIdOfPath(path) { key -> query.firstOrNull { it.first == key }?.second }

    @Test fun `the algorithmic home and feeds go to subscriptions`() {
        for (path in listOf("", "/", "/index", "/feed/explore", "/feed/trending", "/feed/storefront", "/feed/")) {
            assertEquals(path, home, YouTubeNetwork.decidePath(path))
        }
    }

    @Test fun `a Short opens as a plain video, its id kept as is`() {
        assertEquals(Nav.Redirect("https://m.youtube.com/watch?v=aB3_x-Z9"), YouTubeNetwork.decidePath("/shorts/aB3_x-Z9"))
        assertEquals(Nav.Redirect("https://m.youtube.com/watch?v=aB3_x-Z9"), YouTubeNetwork.decidePath("/Shorts/aB3_x-Z9/"))
        assertEquals(home, YouTubeNetwork.decidePath("/shorts/"))
    }

    @Test fun `a channel's Shorts tab goes to its videos`() {
        assertEquals(Nav.Redirect("https://m.youtube.com/@Veritasium/videos"), YouTubeNetwork.decidePath("/@Veritasium/shorts"))
        assertEquals(Nav.Redirect("https://m.youtube.com/channel/UCabc/videos"), YouTubeNetwork.decidePath("/channel/UCabc/shorts/"))
    }

    @Test fun `a hashtag opens search for it`() =
        assertEquals(Nav.Redirect(YouTubeNetwork.SEARCH_URL + "?search_query=%23vicevi"), YouTubeNetwork.decidePath("/hashtag/vicevi"))

    @Test fun `own pages, search, videos and channels stay`() {
        for (path in listOf("/feed/subscriptions", "/feed/library", "/feed/you", "/feed/history", "/feed/playlists",
                "/results", "/watch", "/playlist", "/@Veritasium", "/@Veritasium/videos", "/channel/UCabc")) {
            assertEquals(path, Nav.Allow, YouTubeNetwork.decidePath(path))
        }
    }

    @Test fun `video pages give their id`() {
        assertEquals("WbM7GP7QoEM", postId("/watch", "v" to "WbM7GP7QoEM"))
        assertEquals("aB3_x-Z9", postId("/shorts/aB3_x-Z9"))
        assertEquals("live1", postId("/live/live1"))
        assertEquals(null, postId("/watch"))
        assertEquals(null, postId("/feed/subscriptions"))
        assertEquals(null, postId("/@Veritasium"))
    }

    @Test fun `user agent goes out unchanged`() {
        val ua = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Build/AP; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/138.0.7204.63 Mobile Safari/537.36"
        assertEquals(ua, YouTubeNetwork.userAgent(ua))
    }
}
