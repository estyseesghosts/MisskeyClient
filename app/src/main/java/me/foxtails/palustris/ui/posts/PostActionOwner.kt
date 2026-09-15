package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ReportRequest
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.sourceErrorMessage

data class PostActionTarget(
    val ownedPost: OwnedPost,
    val anchorBounds: Rect,
    val ownerAccountId: AccountId,
    val sessionRevision: Long,
) {
    val post get() = ownedPost.post
    val author get() = ownedPost.post.author
}

enum class RelationshipMutation { Follow, Unfollow, Block, Unblock, Mute, Unmute }

data class PostRelationshipState(
    val target: AccountId? = null,
    val relationship: ProfileRelationship? = null,
    val loading: Boolean = false,
    val mutation: RelationshipMutation? = null,
    val error: String? = null,
)

data class PostReportState(
    val submitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
)

/**
 * Narrow presentation contract for the post-action popup.
 *
 * Generic leaves open, dismiss, and drive the popup through this interface.
 * They never receive the service-backed [PostActionOwner], its [SocialSource],
 * or its coroutine scope.
 */
interface PostPopupPresentation {
    val target: PostActionTarget?
    val relationship: PostRelationshipState
    val report: PostReportState
    fun open(ownedPost: OwnedPost, anchorBounds: Rect)
    fun dismiss()
    fun mutate(mutation: RelationshipMutation)
    fun submitReport(comment: String)
}

internal val LocalPostActionOwner = staticCompositionLocalOf<PostPopupPresentation?> { null }

/**
 * Owns one post-action popup and its account-scoped relationship requests.
 *
 * The owner is bound to one connected account and durable session revision. The host retires
 * it with the connected entry. A retired owner dismisses its popup and rejects later opens,
 * mutations, and reports, so a retired popup has no authority.
 */
class PostActionOwner(
    private val accountId: AccountId,
    private val sessionRevision: Long,
    private val source: SocialSource?,
    private val scope: CoroutineScope,
    private val onRelationshipChanged: () -> Unit = {},
) : PostPopupPresentation {
    override var target by mutableStateOf<PostActionTarget?>(null)
        private set
    override var relationship by mutableStateOf(PostRelationshipState())
        private set
    override var report by mutableStateOf(PostReportState())
        private set

    private var requestGeneration = 0L
    private var requestJob: Job? = null
    private var retired = false

    /**
     * Retires the popup with its connected entry. The popup dismisses and later opens,
     * mutations, and reports are rejected. Retirement is terminal for this owner. The host
     * creates a new owner for the next connected lifetime.
     */
    fun retire() {
        retired = true
        dismiss()
    }

    override fun open(ownedPost: OwnedPost, anchorBounds: Rect) {
        if (retired) return
        if (ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return
        requestJob?.cancel()
        requestGeneration += 1
        val generation = requestGeneration
        target = PostActionTarget(ownedPost, anchorBounds, accountId, sessionRevision)
        relationship = PostRelationshipState(target = ownedPost.post.author.id, loading = true)
        report = PostReportState()
        val relationshipSource = source ?: run {
            relationship = relationship.copy(loading = false, error = "Relationship actions are unavailable.")
            return
        }
        if (ownedPost.post.author.id == accountId) {
            relationship = PostRelationshipState(
                target = accountId,
                relationship = ProfileRelationship(accountId),
                loading = false,
            )
            return
        }
        requestJob = scope.launch {
            try {
                val value = relationshipSource.profileRelationship(ownedPost.post.author.id)
                if (isCurrent(generation, ownedPost.post.author.id)) {
                    relationship = PostRelationshipState(
                        target = value.profileId,
                        relationship = value,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(generation, ownedPost.post.author.id)) {
                    relationship = PostRelationshipState(
                        target = ownedPost.post.author.id,
                        error = sourceErrorMessage(error),
                    )
                }
            }
        }
    }

    override fun dismiss() {
        requestGeneration += 1
        requestJob?.cancel()
        requestJob = null
        target = null
        relationship = PostRelationshipState()
        report = PostReportState()
    }

    override fun mutate(mutation: RelationshipMutation) {
        if (retired) return
        val currentTarget = target ?: return
        relationship.relationship ?: return
        if (relationship.loading || relationship.mutation != null || currentTarget.author.id == accountId) return
        val relationshipSource = source ?: return
        val generation = requestGeneration
        val authorId = currentTarget.author.id
        relationship = relationship.copy(mutation = mutation, error = null)
        requestJob = scope.launch {
            try {
                val value = when (mutation) {
                    RelationshipMutation.Follow -> relationshipSource.followProfile(authorId)
                    RelationshipMutation.Unfollow -> relationshipSource.unfollowProfile(authorId)
                    RelationshipMutation.Block -> relationshipSource.setBlocked(authorId, true)
                    RelationshipMutation.Unblock -> relationshipSource.setBlocked(authorId, false)
                    RelationshipMutation.Mute -> relationshipSource.setMuted(authorId, true)
                    RelationshipMutation.Unmute -> relationshipSource.setMuted(authorId, false)
                }
                if (isCurrent(generation, authorId)) {
                    relationship = PostRelationshipState(target = value.profileId, relationship = value)
                    onRelationshipChanged()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(generation, authorId)) {
                    relationship = relationship.copy(mutation = null, error = sourceErrorMessage(error))
                }
            }
        }
    }

    override fun submitReport(comment: String) {
        if (retired) return
        val currentTarget = target ?: return
        val reportSource = source ?: return
        if (report.submitting || report.submitted || currentTarget.author.id == accountId) return
        val generation = requestGeneration
        report = PostReportState(submitting = true)
        scope.launch {
            try {
                reportSource.report(
                    ReportRequest(
                        targetAccountId = currentTarget.author.id,
                        comment = comment,
                    ),
                )
                if (isCurrent(generation, currentTarget.author.id)) report = PostReportState(submitted = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(generation, currentTarget.author.id)) {
                    report = PostReportState(error = sourceErrorMessage(error))
                }
            }
        }
    }

    private fun isCurrent(generation: Long, authorId: AccountId): Boolean =
        generation == requestGeneration && target?.author?.id == authorId &&
            target?.ownerAccountId == accountId && target?.sessionRevision == sessionRevision
}
