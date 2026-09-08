package me.foxtails.palustris.domain

/**
 * Maps one protocol-neutral activity to the selectable notification categories it belongs to.
 * Replies, mentions, and quotes are intentionally not Social; category policy must be identical
 * for inbox filtering, local delivery, and adapter alert settings.
 */
fun NotificationActivity.categories(): Set<NotificationCategory> = when (this) {
    NotificationActivity.Mention -> setOf(NotificationCategory.Mentions)
    NotificationActivity.Reply -> setOf(NotificationCategory.Replies)
    NotificationActivity.Quote,
    NotificationActivity.QuotedPostUpdate,
    -> setOf(NotificationCategory.Quotes)
    is NotificationActivity.PollResult -> setOf(NotificationCategory.Polls)
    is NotificationActivity.System,
    is NotificationActivity.Unknown,
    -> setOf(NotificationCategory.System)
    NotificationActivity.Reshare,
    NotificationActivity.Favourite,
    is NotificationActivity.EmojiReaction,
    NotificationActivity.Follow,
    NotificationActivity.FollowRequest,
    NotificationActivity.AcceptedRequest,
    NotificationActivity.SubscribedPost,
    NotificationActivity.PostUpdate,
    -> setOf(NotificationCategory.Social)
}

fun NotificationActivity.matchesCategory(category: NotificationCategory): Boolean =
    category == NotificationCategory.All || category in categories()
