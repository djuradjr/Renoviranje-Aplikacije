package com.example.stayfree.domain.cleanfeed

import com.example.stayfree.domain.cleanfeed.CleanFeedRules.Nav
import org.junit.Assert.assertEquals
import org.junit.Test

/** Path rules only: decide() needs android.net.Uri, which plain unit tests lack. */
class CleanFeedRulesTest {

    private val home = Nav.Redirect(CleanFeedRules.HOME_URL)

    @Test fun `following feed is allowed`() =
        assertEquals(Nav.Allow, CleanFeedRules.decidePath("/", "following"))

    @Test fun `favorites feed is allowed`() =
        assertEquals(Nav.Allow, CleanFeedRules.decidePath("/", "favorites"))

    @Test fun `algorithmic home goes to following`() {
        assertEquals(home, CleanFeedRules.decidePath("/", null))
        assertEquals(home, CleanFeedRules.decidePath("", null))
    }

    @Test fun `reels feed goes to following`() {
        assertEquals(home, CleanFeedRules.decidePath("/reels/", null))
        assertEquals(home, CleanFeedRules.decidePath("/reels/C123abc/", null))
        assertEquals(home, CleanFeedRules.decidePath("/Reels", null))
    }

    @Test fun `a single reel from a followed account stays`() =
        assertEquals(Nav.Allow, CleanFeedRules.decidePath("/reel/C123abc/", null))

    @Test fun `explore goes to search, search stays`() {
        val search = Nav.Redirect(CleanFeedRules.SEARCH_URL)
        assertEquals(search, CleanFeedRules.decidePath("/explore/", null))
        assertEquals(search, CleanFeedRules.decidePath("/explore/tags/cats/", null))
        assertEquals(Nav.Allow, CleanFeedRules.decidePath("/explore/search/", null))
    }

    @Test fun `post and reel pages give their id, case kept`() {
        assertEquals("C123abC", CleanFeedRules.postIdOfPath("/p/C123abC/"))
        assertEquals("DgM1-D_x", CleanFeedRules.postIdOfPath("/reel/DgM1-D_x/"))
        assertEquals("C1", CleanFeedRules.postIdOfPath("/some.friend/p/C1/"))
        assertEquals(null, CleanFeedRules.postIdOfPath("/some.friend/"))
        assertEquals(null, CleanFeedRules.postIdOfPath("/reels/"))
        assertEquals(null, CleanFeedRules.postIdOfPath("/direct/inbox/"))
    }

    @Test fun `profiles, posts and messages stay`() {
        assertEquals(Nav.Allow, CleanFeedRules.decidePath("/some.friend/", null))
        assertEquals(Nav.Allow, CleanFeedRules.decidePath("/p/C123abc/", null))
        assertEquals(Nav.Allow, CleanFeedRules.decidePath("/direct/inbox/", null))
        assertEquals(Nav.Allow, CleanFeedRules.decidePath("/accounts/login/", null))
    }
}
