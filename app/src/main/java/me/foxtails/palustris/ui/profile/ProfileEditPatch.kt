package me.foxtails.palustris.ui.profile

import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfilePatch

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
