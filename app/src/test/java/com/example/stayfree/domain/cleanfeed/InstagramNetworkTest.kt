package com.example.stayfree.domain.cleanfeed

import com.example.stayfree.domain.cleanfeed.CleanNetwork.Nav
import org.junit.Assert.assertEquals
import org.junit.Test

/** Path rules only: decide() needs android.net.Uri, which plain unit tests lack. */
class InstagramNetworkTest {

    private val home = Nav.Redirect(InstagramNetwork.HOME_URL)

    @Test fun `following feed is allowed`() =
        assertEquals(Nav.Allow, InstagramNetwork.decidePath("/", "following"))

    @Test fun `favorites feed is allowed`() =
        assertEquals(Nav.Allow, InstagramNetwork.decidePath("/", "favorites"))

    @Test fun `algorithmic home goes to following`() {
        assertEquals(home, InstagramNetwork.decidePath("/", null))
        assertEquals(home, InstagramNetwork.decidePath("", null))
    }

    @Test fun `reels feed goes to following`() {
        assertEquals(home, InstagramNetwork.decidePath("/reels/", null))
        assertEquals(home, InstagramNetwork.decidePath("/reels/C123abc/", null))
        assertEquals(home, InstagramNetwork.decidePath("/Reels", null))
    }

    @Test fun `a single reel from a followed account stays`() =
        assertEquals(Nav.Allow, InstagramNetwork.decidePath("/reel/C123abc/", null))

    @Test fun `explore goes to search, search stays`() {
        val search = Nav.Redirect(InstagramNetwork.SEARCH_URL)
        assertEquals(search, InstagramNetwork.decidePath("/explore/", null))
        assertEquals(search, InstagramNetwork.decidePath("/explore/tags/cats/", null))
        assertEquals(Nav.Allow, InstagramNetwork.decidePath("/explore/search/", null))
    }

    @Test fun `post and reel pages give their id, case kept`() {
        assertEquals("C123abC", InstagramNetwork.postIdOfPath("/p/C123abC/"))
        assertEquals("DgM1-D_x", InstagramNetwork.postIdOfPath("/reel/DgM1-D_x/"))
        assertEquals("C1", InstagramNetwork.postIdOfPath("/some.friend/p/C1/"))
        assertEquals(null, InstagramNetwork.postIdOfPath("/some.friend/"))
        assertEquals(null, InstagramNetwork.postIdOfPath("/reels/"))
        assertEquals(null, InstagramNetwork.postIdOfPath("/direct/inbox/"))
    }

    @Test fun `profiles, posts and messages stay`() {
        assertEquals(Nav.Allow, InstagramNetwork.decidePath("/some.friend/", null))
        assertEquals(Nav.Allow, InstagramNetwork.decidePath("/p/C123abc/", null))
        assertEquals(Nav.Allow, InstagramNetwork.decidePath("/direct/inbox/", null))
        assertEquals(Nav.Allow, InstagramNetwork.decidePath("/accounts/login/", null))
    }
}
