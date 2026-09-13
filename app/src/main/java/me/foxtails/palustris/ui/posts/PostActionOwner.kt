package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.ProfileRelationship
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

/** Owns one post-action popup and its account-scoped relationship requests. */
class PostActionOwner(
    private val accountId: AccountId,
    private val sessionRevision: Long,
    private val source: SocialSource?,
    private val scope: CoroutineScope,
    private val onRelationshipChanged: () -> Unit = {},
) {
    var target by mutableStateOf<PostActionTarget?>(null)
        private set
    var relationship by mutableStateOf(PostRelationshipState())
        private set

    private var requestGeneration = 0L
    private var requestJob: Job? = null

    fun open(ownedPost: OwnedPost, anchorBounds: Rect) {
        if (ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return
        requestJob?.cancel()
        requestGeneration += 1
        val generation = requestGeneration
        target = PostActionTarget(ownedPost, anchorBounds, accountId, sessionRevision)
        relationship = PostRelationshipState(target = ownedPost.post.author.id, loading = true)
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

    fun dismiss() {
        requestGeneration += 1
        requestJob?.cancel()
        requestJob = null
        target = null
        relationship = PostRelationshipState()
    }

    fun mutate(mutation: RelationshipMutation) {
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

    private fun isCurrent(generation: Long, authorId: AccountId): Boolean =
        generation == requestGeneration && target?.author?.id == authorId &&
            target?.ownerAccountId == accountId && target?.sessionRevision == sessionRevision
}
