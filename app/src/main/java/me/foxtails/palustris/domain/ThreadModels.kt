package me.foxtails.palustris.domain

/** Identity of one authenticated thread acquisition. */
data class ThreadSessionKey(
    val fetchingAccount: AccountId,
    val sessionRevision: Long,
    val focalId: EntityId,
)

/** Adapter-owned continuation. The token has no meaning outside its source session. */
data class ThreadContinuation(
    val sessionKey: ThreadSessionKey,
    val token: String,
)

sealed interface ThreadLimitation {
    data class RequestLimit(val used: Int, val maximum: Int) : ThreadLimitation
    data class NodeLimit(val loaded: Int, val maximum: Int) : ThreadLimitation
    data class AncestorLimit(val loaded: Int, val maximum: Int) : ThreadLimitation
    data class DepthLimit(val depth: Int, val maximum: Int) : ThreadLimitation
    data class UnavailableParent(val parentId: EntityId) : ThreadLimitation
    data class BranchFailure(val parentId: EntityId, val error: SourceError) : ThreadLimitation
    data object UncertainServerTruncation : ThreadLimitation
    data object BatchTimeLimit : ThreadLimitation
}

enum class ThreadAcquisitionState {
    Finished,
    HasContinuation,
    Limited,
}

data class ThreadRefreshHint(val minimumDelayMillis: Long) {
    init {
        require(minimumDelayMillis >= 0L)
    }
}

/** Flat, protocol-neutral result returned by an authenticated adapter. */
data class ThreadContext(
    val focal: Post,
    val ancestors: List<Post> = emptyList(),
    val descendants: List<Post> = emptyList(),
    val continuation: ThreadContinuation? = null,
    val limitations: List<ThreadLimitation> = emptyList(),
    val acquisitionState: ThreadAcquisitionState = when {
        continuation != null -> ThreadAcquisitionState.HasContinuation
        limitations.isNotEmpty() -> ThreadAcquisitionState.Limited
        else -> ThreadAcquisitionState.Finished
    },
    val refreshHint: ThreadRefreshHint? = null,
)

data class ThreadConnectorInfo(
    val verifiedParent: Boolean,
    val hasFollowingSibling: Boolean,
)

/** A flat display row. Semantic depth is never replaced by its capped visual depth. */
data class ThreadRow(
    val ownedPost: OwnedPost,
    val parentId: EntityId?,
    val semanticDepth: Int,
    val branchId: String,
    val connector: ThreadConnectorInfo,
    val parentMissing: Boolean,
    val stableKey: String,
) {
    val post: Post get() = ownedPost.post
    val depth: Int get() = semanticDepth
    val visualDepth: Int get() = semanticDepth.coerceAtMost(2)
}

data class ThreadTree(
    val ancestors: List<ThreadRow>,
    val replies: List<ThreadRow>,
    val disconnected: List<ThreadRow>,
) {
    val allRows: List<ThreadRow> get() = ancestors + replies + disconnected
}

fun Post.effectiveTargetId(): EntityId = actionTargetId ?: id

fun OwnedPost.effectiveTargetId(): EntityId = post.effectiveTargetId()
