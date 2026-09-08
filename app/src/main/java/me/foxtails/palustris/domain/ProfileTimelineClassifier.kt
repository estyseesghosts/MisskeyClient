package me.foxtails.palustris.domain

/** Applies the same profile-feed semantics after either adapter has mapped a post. */
fun Post.matchesProfileTimeline(query: ProfileTimelineQuery): Boolean {
    val pureReshare = resharedBy != null
    val performedBy = resharedBy?.id ?: author.id
    if (performedBy != query.profileId) return false
    return when (query.tab) {
        ProfileTimelineTab.Posts -> replyTo == null && !pureReshare
        ProfileTimelineTab.Media -> attachments.isNotEmpty() && !pureReshare
        ProfileTimelineTab.Reposts -> pureReshare && resharedBy?.id == query.profileId
        ProfileTimelineTab.Replies -> replyTo != null && replyToAuthorId != null &&
            replyToAuthorId != query.profileId && !pureReshare
    }
}
