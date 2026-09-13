package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ThreadLimitation
import me.foxtails.palustris.domain.ThreadSessionKey
import me.foxtails.palustris.domain.EntityId

internal data class ChildWork(
    val parentId: EntityId,
    val depth: Int,
    val cursor: String?,
)

internal data class ThreadAcquisition(
    val key: ThreadSessionKey,
    val focal: Post,
    val ancestors: MutableList<Post>,
    val descendants: MutableList<Post>,
    val pending: ArrayDeque<ChildWork>,
    val visitedRequests: MutableSet<ChildWork>,
    val limitations: MutableList<ThreadLimitation>,
    var requestsUsed: Int,
    var token: String? = null,
    var hardLimitReached: Boolean = false,
)
