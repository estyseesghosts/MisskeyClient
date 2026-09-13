package me.foxtails.palustris.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.large.LargeNavTarget

internal enum class Destination(@StringRes val labelRes: Int, val icon: ImageVector) {
    Home(R.string.nav_home, AppIcons.Home), Search(R.string.nav_search, AppIcons.Search),
    Notifications(R.string.nav_notifications, AppIcons.Notifications), Profile(R.string.nav_profile, AppIcons.Person),
}

internal enum class NotificationsPanel { Notifications, DirectMessages }
internal enum class LocalPage { SavedPosts, Likes, Drafts, About }
enum class SearchPanel { Search, PhotoGrid }
internal enum class LargePostOrigin { Home, Search, PhotoGrid, Profile, Saved, Liked, Notification, Other }

internal fun LargePostOrigin.supportsComments(): Boolean = this == LargePostOrigin.Home ||
    this == LargePostOrigin.Search || this == LargePostOrigin.PhotoGrid || this == LargePostOrigin.Profile ||
    this == LargePostOrigin.Saved || this == LargePostOrigin.Liked || this == LargePostOrigin.Notification

internal fun LargePostOrigin.singlePostPresentation(): SinglePostPresentation =
    if (this == LargePostOrigin.PhotoGrid) SinglePostPresentation.PhotoGrid else SinglePostPresentation.Standard
internal sealed interface Overlay {
    data object Composer : Overlay
    data object EditProfile : Overlay
    data object NotificationSettings : Overlay
}

internal fun largeTargetFor(
    destination: Destination,
    searchPanel: SearchPanel,
    notificationsPanel: NotificationsPanel,
): LargeNavTarget = when (destination) {
    Destination.Home -> LargeNavTarget.Home
    Destination.Search -> if (searchPanel == SearchPanel.Search) LargeNavTarget.Search else LargeNavTarget.PhotoGrid
    Destination.Notifications -> if (notificationsPanel == NotificationsPanel.Notifications) LargeNavTarget.Notifications else LargeNavTarget.DirectMessages
    Destination.Profile -> LargeNavTarget.Profile
}

@StringRes
internal fun savedCollectionTitle(kind: SavedPostsKind?): Int = when (kind) {
    SavedPostsKind.Favourites -> R.string.collection_favourites
    SavedPostsKind.Bookmarks, null -> R.string.collection_bookmarks
}

@StringRes
internal fun likedCollectionTitle(): Int = R.string.collection_likes

@StringRes
internal fun timelineLabelRes(timeline: Timeline): Int = when (timeline) {
    Timeline.Home -> R.string.timeline_home
    Timeline.Local -> R.string.timeline_local
    Timeline.Social -> R.string.timeline_social
    Timeline.Bubble -> R.string.timeline_bubble
    Timeline.Federated -> R.string.timeline_federated
}

@StringRes
internal fun timelineDescriptionRes(timeline: Timeline): Int = when (timeline) {
    Timeline.Home -> R.string.timeline_description_home
    Timeline.Local -> R.string.timeline_description_local
    Timeline.Social -> R.string.timeline_description_social
    Timeline.Bubble -> R.string.timeline_description_bubble
    Timeline.Federated -> R.string.timeline_description_federated
}

