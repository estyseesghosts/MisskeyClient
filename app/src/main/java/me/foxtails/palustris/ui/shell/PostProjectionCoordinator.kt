package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost

/**
 * One fan-out point for normalized post updates and accepted publications.
 *
 * The coordinator forwards an event to every registered sink except the origin. Nested forwarding
 * is suppressed, so a sink that re-emits while it applies an update cannot start a cycle. The
 * coordinator performs no network work, keeps no post copy, and makes no rollback decision.
 *
 * An update must match the bound account and durable revision. A foreign or stale update is
 * rejected before any sink sees it.
 */
class PostProjectionCoordinator(
    private val boundAccountId: AccountId? = null,
    private val boundRevision: Long = 0L,
) {
    private val sinks = mutableListOf<Sink>()
    private var forwarding = false

    fun register(sink: Sink) {
        if (sink !in sinks) sinks += sink
    }

    fun unregister(sink: Sink) {
        sinks -= sink
    }

    fun clear() {
        sinks.clear()
    }

    /** Forwards a normalized external update from [origin]. */
    fun forwardExternalPost(origin: Sink, updated: OwnedPost) {
        if (!accepts(updated)) return
        dispatch(origin) { it.applyExternalPost(updated) }
    }

    /** Forwards one accepted publication from [origin]. */
    fun forwardPublishedPost(origin: Sink, request: CreatePostRequest, created: OwnedPost) {
        if (!accepts(created)) return
        dispatch(origin) {
            it.applyPublishedPost(request)
            it.acceptPublishedReply(created)
            it.acceptPublishedQuote(request.quoteOf)
        }
    }

    private fun accepts(updated: OwnedPost): Boolean {
        if (boundAccountId == null) return true
        return updated.fetchedBy == boundAccountId && updated.sessionRevision == boundRevision
    }

    private fun dispatch(origin: Sink, action: (Sink) -> Unit) {
        if (forwarding) return
        forwarding = true
        try {
            sinks.toList().forEach { sink -> if (sink !== origin) action(sink) }
        } finally {
            forwarding = false
        }
    }

    /** One receiving owner. Stable identity lets the coordinator exclude the origin. */
    interface Sink {
        fun applyExternalPost(updated: OwnedPost)
        fun applyPublishedPost(request: CreatePostRequest) = Unit
        fun acceptPublishedReply(created: OwnedPost) = Unit
        fun acceptPublishedQuote(target: EntityId?) = Unit
    }
}
