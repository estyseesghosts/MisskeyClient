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

sealed interface ProfileChipEntry {
    data class Timeline(val category: ProfileCategory) : ProfileChipEntry
    data object Drafts : ProfileChipEntry
    data object Bookmarks : ProfileChipEntry
}

fun profileChipEntries(isSelf: Boolean): List<ProfileChipEntry> = buildList {
    addAll(
        listOf(
            ProfileChipEntry.Timeline(ProfileCategory.Posts),
            ProfileChipEntry.Timeline(ProfileCategory.Media),
            ProfileChipEntry.Timeline(ProfileCategory.Reposts),
            ProfileChipEntry.Timeline(ProfileCategory.Replies),
        ),
    )
    if (isSelf) {
        add(ProfileChipEntry.Drafts)
        add(ProfileChipEntry.Bookmarks)
    }
    add(ProfileChipEntry.Timeline(ProfileCategory.ShowMore))
}
