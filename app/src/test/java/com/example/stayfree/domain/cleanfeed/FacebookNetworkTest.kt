package com.example.stayfree.domain.cleanfeed

import com.example.stayfree.domain.cleanfeed.CleanNetwork.Nav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Path rules only: decide() needs android.net.Uri, which plain unit tests lack. */
class FacebookNetworkTest {

    private val home = Nav.Redirect(FacebookNetwork.HOME_URL)

    private fun decide(path: String, vararg query: Pair<String, String>): Nav =
        FacebookNetwork.decidePath(path) { key -> query.firstOrNull { it.first == key }?.second }

    private fun postId(path: String, vararg query: Pair<String, String>): String? =
        FacebookNetwork.postIdOfPath(path) { key -> query.firstOrNull { it.first == key }?.second }

    @Test fun `chronological feeds are allowed`() {
        for (feed in listOf("all", "friends", "favorites", "groups", "pages")) {
            assertEquals(feed, Nav.Allow, decide("/", "filter" to feed, "sk" to "h_chr"))
        }
    }

    @Test fun `algorithmic home goes to the chronological feed`() {
        assertEquals(home, decide("/"))
        assertEquals(home, decide(""))
        assertEquals(home, decide("/home.php"))
        assertEquals(home, decide("/", "sk" to "h_chr"))
        assertEquals(home, decide("/", "filter" to "all"))
        assertEquals(home, decide("/", "filter" to "videos", "sk" to "h_chr"))
    }

    @Test fun `reels, the video feed, marketplace and gaming go home`() {
        assertEquals(home, decide("/reel/"))
        assertEquals(home, decide("/reel/1234567890/"))
        assertEquals(home, decide("/reels/"))
        assertEquals(home, decide("/watch/"))
        assertEquals(home, decide("/watch/live/"))
        assertEquals(home, decide("/Marketplace/"))
        assertEquals(home, decide("/gaming/feed/"))
    }

    @Test fun `a single video stays`() =
        assertEquals(Nav.Allow, decide("/watch/", "v" to "1234567890"))

    @Test fun `a hashtag opens search for it`() {
        val search = Nav.Redirect(FacebookNetwork.SEARCH_URL + "?q=%23vicevi")
        assertEquals(search, decide("/watch/hashtag/vicevi/"))
        assertEquals(search, decide("/hashtag/vicevi"))
    }

    @Test fun `search stays, its video and reel feeds do not`() {
        assertEquals(Nav.Allow, decide("/search/top/", "q" to "marko"))
        assertEquals(Nav.Allow, decide("/search/people/", "q" to "marko"))
        assertEquals(Nav.Redirect(FacebookNetwork.SEARCH_URL + "?q=marko+p"),
            decide("/search/videos/", "q" to "marko p"))
        assertEquals(Nav.Redirect(FacebookNetwork.SEARCH_URL), decide("/search/reels/"))
    }

    @Test fun `profiles, groups, posts, messages and login stay`() {
        for (path in listOf("/vickast.vicevi", "/groups/123/", "/marko/posts/pfbid02abc", "/messages/t/123/",
                "/notifications/", "/stories/122/UzpfSVNDOjIy/", "/login/", "/checkpoint/")) {
            assertEquals(path, Nav.Allow, decide(path))
        }
    }

    @Test fun `post and video pages give their id, photos do not`() {
        assertEquals("pfbid02abcXYZ", postId("/marko/posts/pfbid02abcXYZ/"))
        assertEquals("987", postId("/groups/123/posts/987/"))
        assertEquals("987", postId("/groups/123/permalink/987/"))
        assertEquals("555", postId("/somepage/videos/555/"))
        assertEquals("111", postId("/permalink.php", "story_fbid" to "111", "id" to "4"))
        assertEquals("111", postId("/story.php", "story_fbid" to "111"))
        assertEquals(null, postId("/photo/", "fbid" to "222"))
        assertEquals(null, postId("/photo.php", "fbid" to "222"))
        assertEquals("333", postId("/watch/", "v" to "333"))
        assertEquals(null, postId("/marko/"))
        assertEquals(null, postId("/messages/t/123/"))
        assertEquals(null, postId("/"))
    }

    @Test fun `desktop user agent keeps the WebView's Chrome version`() {
        val ua = FacebookNetwork.userAgent(
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Build/AP; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Version/4.0 Chrome/138.0.7204.63 Mobile Safari/537.36")
        assertTrue(ua, ua.contains("X11; Linux x86_64") && ua.contains("Chrome/138.0.7204.63"))
        assertTrue(ua, !ua.contains("Mobile") && !ua.contains("Android") && !ua.contains("wv"))
    }
}
