package me.foxtails.palustris.data.notifications

import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError

data class NotificationSyncResult(
    val pages: Int,
    val complete: Boolean,
    val unreadState: NotificationUnreadState,
    val delayed: Boolean = false,
)

/** One-shot synchronization algorithms shared by foreground and durable workers. */
class NotificationSynchronizer @Inject constructor(
    private val repository: NotificationRepository,
) {
    suspend fun establishBaseline(
        source: SocialSource,
        token: NotificationSyncToken,
        query: NotificationQuery = NotificationQuery(),
    ): NotificationSyncResult {
        val page = source.notifications(query).copy(direction = NotificationPageDirection.Initial)
        repository.establishBaseline(token, page)
        val unread = readUnread(source)
        if (unread !is NotificationUnreadState.Unknown) repository.updateUnreadState(token, unread)
        return NotificationSyncResult(1, true, unread)
    }

    suspend fun catchUpNewer(
        source: SocialSource,
        token: NotificationSyncToken,
        query: NotificationQuery = NotificationQuery(),
        pageBudget: Int = DEFAULT_PAGE_BUDGET,
    ): NotificationSyncResult {
        var checkpoint = repository.checkpoint(token.accountId, query)
            ?: return establishBaseline(source, token, query)
        var continuation = checkpoint.newerContinuation
        var previousContinuation = continuation
        var pages = 0
        var unread: NotificationUnreadState = NotificationUnreadState.Unknown
        while (pages < pageBudget) {
            val requestCheckpoint = checkpoint.copy(newerContinuation = continuation)
            val page = source.fetchNewerNotifications(query, requestCheckpoint)
                .copy(direction = NotificationPageDirection.Newer)
            if (page.items.isEmpty() && page.resolvedContinuation == previousContinuation && page.resolvedContinuation != null) {
                return catchUpResult(source, token, pages, complete = false, delayed = true, unread)
            }
            repository.ingestNewerPage(token, page)
            pages += 1
            unread = page.unreadState.takeUnless { it is NotificationUnreadState.Unknown } ?: unread
            checkpoint = repository.checkpoint(token.accountId, query) ?: checkpoint
            continuation = checkpoint.newerContinuation
            if (continuation == null) {
                return catchUpResult(source, token, pages, complete = true, delayed = false, unread)
            }
            if (continuation == previousContinuation) {
                return catchUpResult(source, token, pages, complete = false, delayed = true, unread)
            }
            previousContinuation = continuation
        }
        return catchUpResult(source, token, pages, complete = false, delayed = true, unread)
    }

    private suspend fun catchUpResult(
        source: SocialSource,
        token: NotificationSyncToken,
        pages: Int,
        complete: Boolean,
        delayed: Boolean,
        fallbackUnread: NotificationUnreadState,
    ): NotificationSyncResult {
        val refreshedUnread = readUnread(source)
        if (refreshedUnread !is NotificationUnreadState.Unknown) {
            repository.updateUnreadState(token, refreshedUnread)
        }
        return NotificationSyncResult(
            pages = pages,
            complete = complete,
            unreadState = refreshedUnread.takeUnless { it is NotificationUnreadState.Unknown } ?: fallbackUnread,
            delayed = delayed,
        )
    }

    suspend fun loadOlder(
        source: SocialSource,
        token: NotificationSyncToken,
        query: NotificationQuery = NotificationQuery(),
    ): NotificationSyncResult {
        val checkpoint = repository.checkpoint(token.accountId, query)
            ?: return establishBaseline(source, token, query)
        if (checkpoint.oldest == null && checkpoint.olderContinuation == null) {
            return NotificationSyncResult(0, true, repository.observe(token.accountId).value.unreadState)
        }
        val page = source.fetchOlderNotifications(query, checkpoint.copy(
            oldest = checkpoint.olderContinuation ?: checkpoint.oldest,
        )).copy(direction = NotificationPageDirection.Older)
        repository.ingestOlderPage(token, page)
        return NotificationSyncResult(1, page.resolvedContinuation == null, page.unreadState)
    }

    suspend fun applyAcknowledgement(
        source: SocialSource,
        token: NotificationSyncToken,
    ): NotificationAcknowledgement {
        val result = source.acknowledgeNotifications()
        repository.applyAcknowledgement(token, result)
        return result
    }

    private suspend fun readUnread(source: SocialSource): NotificationUnreadState = try {
        source.notificationUnreadState()
    } catch (error: CancellationException) {
        throw error
    } catch (error: SourceError.Unsupported) {
        NotificationUnreadState.Unknown
    }

    private companion object {
        const val DEFAULT_PAGE_BUDGET = 4
    }
}
