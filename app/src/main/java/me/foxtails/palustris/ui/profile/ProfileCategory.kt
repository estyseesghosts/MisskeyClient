package me.foxtails.palustris.ui.profile

import me.foxtails.palustris.domain.ProfileTimelineTab

/** The profile categories have an explicit domain mapping so their order cannot drift. */
enum class ProfileCategory(
    val label: String,
    val timelineTab: ProfileTimelineTab?,
) {
    Posts("Posts", ProfileTimelineTab.Posts),
    Media("Media", ProfileTimelineTab.Media),
    Reposts("Reposts", ProfileTimelineTab.Reposts),
    Replies("Replies", ProfileTimelineTab.Replies),
    ShowMore("Show more...", null),
}
