package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost

internal data class PendingRepostConfirmation(
    val fetchedBy: AccountId,
    val postId: EntityId,
    val sessionRevision: Long,
    val anchorBounds: Rect,
    val selected: Boolean,
)

internal class PostRepostConfirmationOwner {
    var pending by mutableStateOf<PendingRepostConfirmation?>(null)
        private set

    fun request(post: OwnedPost, anchorBounds: Rect) {
        pending = PendingRepostConfirmation(
            fetchedBy = post.fetchedBy,
            postId = post.post.id,
            sessionRevision = post.sessionRevision,
            anchorBounds = anchorBounds,
            selected = post.post.reposted,
        )
    }

    fun dismiss() {
        pending = null
    }

    fun reconcile(post: OwnedPost) {
        val current = pending ?: return
        if (current.fetchedBy == post.fetchedBy && current.postId == post.post.id &&
            (current.sessionRevision != post.sessionRevision || current.selected != post.post.reposted)
        ) {
            pending = null
        }
    }

    fun confirm(post: OwnedPost, onReshare: (OwnedPost) -> Unit) {
        val current = pending ?: return
        val samePost = current.fetchedBy == post.fetchedBy &&
            current.postId == post.post.id &&
            current.sessionRevision == post.sessionRevision
        if (samePost && current.selected == post.post.reposted) onReshare(post)
        pending = null
    }
}
