package me.foxtails.palustris

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.matchesProfileTimeline
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTimelineClassifierTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val target = AccountId(connection, "target")
    private val other = AccountId(connection, "other")
    private val targetAccount = Account(target, "Target", "@target@example.org")
    private val otherAccount = Account(other, "Other", "@other@example.org")

    @Test
    fun postsIncludeRootsAndAuthoredQuotesButExcludeRepliesAndPureReposts() {
        assertMatches(root(), ProfileTimelineTab.Posts)
        assertMatches(root(quote = root("quoted", otherAccount)), ProfileTimelineTab.Posts)
        assertNotMatches(reply(other), ProfileTimelineTab.Posts)
        assertNotMatches(pureReshare(), ProfileTimelineTab.Posts)
    }

    @Test
    fun mediaIncludesTargetAuthoredAttachmentsIncludingRepliesButNotBoostedMedia() {
        assertMatches(root(attachments = listOf(attachment())), ProfileTimelineTab.Media)
        assertMatches(reply(other, attachments = listOf(attachment())), ProfileTimelineTab.Media)
        assertNotMatches(pureReshare(attachments = listOf(attachment())), ProfileTimelineTab.Media)
    }

    @Test
    fun repostsIncludeOnlyPureResharesPerformedByTheTarget() {
        assertMatches(pureReshare(), ProfileTimelineTab.Reposts)
        assertNotMatches(root(quote = root("quoted", otherAccount)), ProfileTimelineTab.Reposts)
        assertNotMatches(root(), ProfileTimelineTab.Reposts)
    }

    @Test
    fun repliesRequireAKnownParentOwnedByAnotherAccount() {
        assertMatches(reply(other), ProfileTimelineTab.Replies)
        assertNotMatches(reply(target), ProfileTimelineTab.Replies)
        assertNotMatches(root(replyTo = EntityId(connection.origin, "unknown")), ProfileTimelineTab.Replies)
        assertNotMatches(root(), ProfileTimelineTab.Replies)
    }

    @Test
    fun classifierScopesOrdinaryPostsToTheRequestedAccount() {
        val foreign = Post(
            id = EntityId(connection.origin, "foreign"),
            author = otherAccount,
            text = "Not target content",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
        )

        assertFalse(foreign.matchesProfileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts)))
        assertTrue(root().matchesProfileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts)))
    }

    @Test
    fun accountIdentityUsesOriginAndLocalIdAcrossProtocolMetadata() {
        val sameIdentity = AccountId(Connection(connection.origin, Protocol.MISSKEY), target.localId)
        assertTrue(root().matchesProfileTimeline(ProfileTimelineQuery(sameIdentity, ProfileTimelineTab.Posts)))
    }

    private fun assertMatches(post: Post, tab: ProfileTimelineTab) {
        assertTrue(post.matchesProfileTimeline(ProfileTimelineQuery(target, tab)))
    }

    private fun assertNotMatches(post: Post, tab: ProfileTimelineTab) {
        assertFalse(post.matchesProfileTimeline(ProfileTimelineQuery(target, tab)))
    }

    private fun root(
        id: String = "root",
        author: Account = targetAccount,
        attachments: List<Attachment> = emptyList(),
        replyTo: EntityId? = null,
        replyToAuthorId: AccountId? = null,
        quote: Post? = null,
    ) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = "Post $id",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        attachments = attachments,
        replyTo = replyTo,
        replyToAuthorId = replyToAuthorId,
        quote = quote,
        availableActions = setOf(PostAction.Reply),
    )

    private fun reply(parentAuthorId: AccountId, attachments: List<Attachment> = emptyList()): Post = root(
        id = "reply-${parentAuthorId.localId}",
        attachments = attachments,
        replyTo = EntityId(connection.origin, "parent-${parentAuthorId.localId}"),
        replyToAuthorId = parentAuthorId,
    )

    private fun pureReshare(attachments: List<Attachment> = emptyList()): Post = root(
        id = "reshare",
        author = otherAccount,
        attachments = attachments,
    ).copy(resharedBy = targetAccount)

    private fun attachment() = Attachment(
        url = "https://example.org/photo.jpg",
        mimeType = "image/jpeg",
        description = null,
    )
}
