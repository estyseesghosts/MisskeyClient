package me.foxtails.palustris.domain

/** Transport-independent boundary implemented by individual server adapters. */
interface SocialSource {
    val capabilities: ServerCapabilities
    suspend fun timelines(): List<Timeline> = capabilities.timelines.toList()
    suspend fun timeline(timeline: Timeline, cursor: String? = null): Page<Post>
    suspend fun post(id: EntityId): Post = unsupported("post")
    suspend fun profile(id: AccountId): Account = unsupported("profile")
    suspend fun profileTimeline(query: ProfileTimelineQuery, cursor: String? = null): Page<Post> =
        unsupported("profile.timeline")
    suspend fun profileRelationship(id: AccountId): ProfileRelationship = unsupported("profile.relationship")
    suspend fun followProfile(id: AccountId): ProfileRelationship = unsupported("profile.follow")
    suspend fun unfollowProfile(id: AccountId): ProfileRelationship = unsupported("profile.unfollow")
    suspend fun pinnedPosts(id: AccountId): List<Post> = emptyList()
    suspend fun thread(rootId: EntityId): List<Post> = unsupported("thread")
    suspend fun create(post: CreatePostRequest): Post = unsupported("create")
    suspend fun updateProfile(request: UpdateProfileRequest): Account = unsupported("updateProfile")
    suspend fun delete(id: EntityId) = unsupported<Unit>("delete")
    suspend fun edit(id: EntityId, text: String): Post = unsupported("edit")
    suspend fun react(id: EntityId, emoji: String) = unsupported<Unit>("react")
    suspend fun favorite(id: EntityId) = unsupported<Unit>("favorite")
    suspend fun favorite(id: EntityId, favouriteEmoji: String): Unit = favorite(id)
    suspend fun unfavorite(id: EntityId, favouriteEmoji: String? = null) = unsupported<Unit>("favorite")
    suspend fun renote(id: EntityId) = unsupported<Unit>("renote")
    suspend fun unrenote(id: EntityId, ownRepostId: EntityId? = null) = unsupported<Unit>("renote")
    suspend fun removeReaction(id: EntityId, emoji: String) = unsupported<Unit>("react")
    suspend fun save(id: EntityId) = unsupported<Unit>("save")
    suspend fun unsave(id: EntityId) = unsupported<Unit>("save")
    suspend fun savedPosts(cursor: String? = null): Page<Post> = unsupported("savedPosts")
    suspend fun setPrimaryFavourite(
        id: EntityId,
        favouriteEmoji: String,
        selected: Boolean,
    ): PostActionResult {
        if (selected) favorite(id, favouriteEmoji) else unfavorite(id, favouriteEmoji)
        return PostActionResult(selected = selected)
    }
    suspend fun setReshared(id: EntityId, selected: Boolean, ownRepostId: EntityId? = null): PostActionResult {
        if (selected) renote(id) else unrenote(id, ownRepostId)
        return PostActionResult(selected = selected, createdRepostId = ownRepostId)
    }
    suspend fun setSaved(id: EntityId, selected: Boolean): PostActionResult {
        if (selected) save(id) else unsave(id)
        return PostActionResult(selected = selected)
    }
    suspend fun quote(id: EntityId, text: String) = unsupported<Unit>("quote")
    suspend fun votePoll(id: EntityId, optionIndex: Int) = unsupported<Unit>("votePoll")
    suspend fun uploadMedia(file: java.io.InputStream, mimeType: String): Attachment = unsupported("uploadMedia")
    suspend fun search(query: String): List<Post> = unsupported("search")
    suspend fun searchHashtag(tag: String, cursor: String? = null): Page<Post> = unsupported("hashtag search")
    suspend fun searchAccounts(query: String): List<Account> = unsupported("account search")
    /** Legacy page shape retained for source compatibility during the adapter migration. */
    suspend fun notifications(cursor: String? = null): Page<Notification> = unsupported("notifications")
    suspend fun notifications(query: NotificationQuery, cursor: NotificationCursor? = null): NotificationPage =
        unsupported("notifications")
    suspend fun fetchNewerNotifications(
        query: NotificationQuery,
        checkpoint: NotificationCheckpoint,
    ): NotificationPage = unsupported("notifications.newer")
    suspend fun fetchOlderNotifications(
        query: NotificationQuery,
        checkpoint: NotificationCheckpoint,
    ): NotificationPage = unsupported("notifications.older")
    suspend fun notificationUnreadState(): NotificationUnreadState = unsupported("notifications.unread")
    suspend fun acknowledgeNotifications(): NotificationAcknowledgement = unsupported("notifications.acknowledge")
    suspend fun dismissNotification(id: EntityId) = unsupported<Unit>("notifications.dismiss")
    suspend fun respondToFollowRequest(targetAccountId: AccountId, accept: Boolean) =
        unsupported<Unit>("notifications.followRequest")
    suspend fun queryOwnedPushSubscription(knownEndpoint: ValidatedUrl? = null): PushSubscription? =
        unsupported("notifications.push.query")
    suspend fun createOrReplacePushSubscription(
        spec: PushSubscriptionSpec,
        previous: PushSubscription? = null,
    ): PushSubscription = unsupported("notifications.push.create-or-replace")
    suspend fun updatePushAlertPolicy(
        subscription: PushSubscription,
        alerts: Set<NotificationCategory>,
    ): PushSubscription = unsupported("notifications.push.policy")
    suspend fun removePushSubscription(subscription: PushSubscription) =
        unsupported<Unit>("notifications.push.remove")
    suspend fun mute(id: EntityId) = unsupported<Unit>("mute")
    suspend fun block(id: EntityId) = unsupported<Unit>("block")
    fun streamEvents(): kotlinx.coroutines.flow.Flow<Event> = kotlinx.coroutines.flow.emptyFlow()
}

internal suspend fun <T> unsupported(feature: String): T = throw SourceError.Unsupported(feature)
