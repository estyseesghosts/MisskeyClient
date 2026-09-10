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
internal enum class LocalPage { SavedPosts, Drafts, About }
enum class SearchPanel { Search, Alternate }
internal enum class LargePostOrigin { Home, Search, Profile, Saved, Notification, Other }
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
    Destination.Search -> if (searchPanel == SearchPanel.Search) LargeNavTarget.Search else LargeNavTarget.AlternateSearch
    Destination.Notifications -> if (notificationsPanel == NotificationsPanel.Notifications) LargeNavTarget.Notifications else LargeNavTarget.DirectMessages
    Destination.Profile -> LargeNavTarget.Profile
}

@StringRes
internal fun savedCollectionTitle(kind: SavedPostsKind?): Int = when (kind) {
    SavedPostsKind.Favourites -> R.string.collection_favourites
    SavedPostsKind.Bookmarks, null -> R.string.collection_bookmarks
}

@StringRes
internal fun timelineLabelRes(timeline: Timeline): Int = when (timeline) {
    Timeline.Home -> R.string.timeline_home
    Timeline.Local -> R.string.timeline_local
    Timeline.Social -> R.string.timeline_social
    Timeline.Federated -> R.string.timeline_federated
}

internal fun editableProfilePatch(base: EditableProfile, edited: EditableProfile): EditableProfilePatch = EditableProfilePatch(
    displayName = edited.displayName.takeIf { it != base.displayName },
    biography = edited.biography.takeIf { it != base.biography },
    fields = edited.fields.takeIf { it != base.fields },
    avatarDescription = edited.avatarDescription.takeIf { it != base.avatarDescription },
    headerDescription = edited.headerDescription.takeIf { it != base.headerDescription },
    locked = edited.locked.takeIf { it != base.locked },
    bot = edited.bot.takeIf { it != base.bot },
    hideCollections = edited.hideCollections.takeIf { it != base.hideCollections },
    discoverable = edited.discoverable.takeIf { it != base.discoverable },
    indexable = edited.indexable.takeIf { it != base.indexable },
    showMedia = edited.showMedia.takeIf { it != base.showMedia },
    showMediaReplies = edited.showMediaReplies.takeIf { it != base.showMediaReplies },
    showFeatured = edited.showFeatured.takeIf { it != base.showFeatured },
    attributionDomains = edited.attributionDomains.takeIf { it != base.attributionDomains },
)
