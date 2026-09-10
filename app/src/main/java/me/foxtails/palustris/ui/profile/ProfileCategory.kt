package me.foxtails.palustris.ui.profile

import androidx.annotation.StringRes
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.ProfileTimelineTab

/** The profile categories have an explicit domain mapping so their order cannot drift. */
enum class ProfileCategory(
    @StringRes val labelRes: Int,
    val timelineTab: ProfileTimelineTab?,
) {
    Posts(R.string.profile_tab_posts, ProfileTimelineTab.Posts),
    Media(R.string.profile_tab_media, ProfileTimelineTab.Media),
    Reposts(R.string.profile_tab_reposts, ProfileTimelineTab.Reposts),
    Replies(R.string.profile_tab_replies, ProfileTimelineTab.Replies),
    ShowMore(R.string.profile_tab_show_more, null),
}

sealed interface ProfileChipEntry {
    data class Timeline(val category: ProfileCategory) : ProfileChipEntry
    data object Drafts : ProfileChipEntry
    data object Bookmarks : ProfileChipEntry
    data object Likes : ProfileChipEntry
    data object EditProfile : ProfileChipEntry
}

fun profileChipEntries(
    isSelf: Boolean,
    includeLikes: Boolean = false,
    includeShowMore: Boolean = true,
    includeEditProfile: Boolean = false,
): List<ProfileChipEntry> = buildList {
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
        if (includeLikes) add(ProfileChipEntry.Likes)
        if (includeEditProfile) add(ProfileChipEntry.EditProfile)
    }
    if (includeShowMore) add(ProfileChipEntry.Timeline(ProfileCategory.ShowMore))
}
