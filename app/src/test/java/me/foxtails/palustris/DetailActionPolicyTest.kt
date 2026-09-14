package me.foxtails.palustris

import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.ui.DetailActions
import me.foxtails.palustris.ui.LargePostOrigin
import me.foxtails.palustris.ui.detailActionsFor
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailActionPolicyTest {
    private val account = AppShellFixtures.account("detail-policy")
    private val post = OwnedPost(
        account.id,
        Post(EntityId(account.id.connection.origin, "post"), account, "text", 0L, Audience.Public),
    )
    private val choice = EmojiChoice("thumbsup", "thumbsup", null)

    private class Recorder {
        var favorite = 0
        var reply = 0
        var reshare = 0
        var bookmark = 0
        var react = 0
        fun fallback() = DetailActions({ favorite++ }, { reply++ }, { reshare++ }, { bookmark++ }, { _, _ -> react++ })
    }

    @Test
    fun likedOriginUsesLikeToggleForFavorite() {
        var toggled = 0
        val recorder = Recorder()
        val actions = detailActionsFor(
            origin = LargePostOrigin.Liked,
            threadActive = false,
            thread = AppShellFixtures.thread(),
            profile = AppShellFixtures.profileContract(),
            bookmarks = AppShellFixtures.bookmarks(),
            likes = AppShellFixtures.likes(onToggle = { toggled++ }),
            fallback = recorder.fallback(),
        )

        actions.favorite(post)
        assertEquals(1, toggled)
        assertEquals(0, recorder.favorite)
    }

    @Test
    fun profileAndSavedOriginsOwnTheirReaction() {
        var profileReactions = 0
        var bookmarkReactions = 0
        val recorder = Recorder()
        val profile = AppShellFixtures.profileContract(onReact = { _, _ -> profileReactions++ })
        val bookmarks = AppShellFixtures.bookmarks(onReact = { _, _ -> bookmarkReactions++ })

        detailActionsFor(LargePostOrigin.Profile, false, AppShellFixtures.thread(), profile, bookmarks, AppShellFixtures.likes(), recorder.fallback()).react(post, choice)
        detailActionsFor(LargePostOrigin.Saved, false, AppShellFixtures.thread(), profile, bookmarks, AppShellFixtures.likes(), recorder.fallback()).react(post, choice)

        assertEquals(1, profileReactions)
        assertEquals(1, bookmarkReactions)
        assertEquals(0, recorder.react)
    }

    @Test
    fun otherOriginUsesFallbackForReaction() {
        val recorder = Recorder()
        detailActionsFor(
            origin = LargePostOrigin.Home,
            threadActive = false,
            thread = AppShellFixtures.thread(),
            profile = AppShellFixtures.profileContract(),
            bookmarks = AppShellFixtures.bookmarks(),
            likes = AppShellFixtures.likes(),
            fallback = recorder.fallback(),
        ).react(post, choice)

        assertEquals(1, recorder.react)
    }

    @Test
    fun activeThreadOwnsEveryMutation() {
        var favorite = 0
        var reshare = 0
        var bookmark = 0
        var react = 0
        val recorder = Recorder()
        val actions = detailActionsFor(
            origin = LargePostOrigin.Home,
            threadActive = true,
            thread = AppShellFixtures.thread(
                onFavorite = { favorite++ },
                onRepost = { reshare++ },
                onBookmark = { bookmark++ },
                onReact = { _, _ -> react++ },
            ),
            profile = AppShellFixtures.profileContract(),
            bookmarks = AppShellFixtures.bookmarks(),
            likes = AppShellFixtures.likes(),
            fallback = recorder.fallback(),
        )

        actions.favorite(post)
        actions.reshare(post)
        actions.bookmark(post)
        actions.react(post, choice)
        actions.reply(post)

        assertEquals(1, favorite)
        assertEquals(1, reshare)
        assertEquals(1, bookmark)
        assertEquals(1, react)
        assertEquals(1, recorder.reply)
    }
}
