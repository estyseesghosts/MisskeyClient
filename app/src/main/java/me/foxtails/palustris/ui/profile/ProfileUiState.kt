package me.foxtails.palustris.ui.profile

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfileCapabilities
import me.foxtails.palustris.domain.EditableProfileField
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ProfileTimelineTab

data class ProfilePageState(
    val posts: List<OwnedPost> = emptyList(),
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
    val needsSignIn: Boolean = false,
    val terminal: Boolean = false,
    val consecutiveEmptyPages: Int = 0,
)

data class ProfileUiState(
    val targetId: AccountId? = null,
    val seedAccount: Account? = null,
    val account: Account? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val detailNeedsSignIn: Boolean = false,
    val staleDetails: Boolean = false,
    val relationship: ProfileRelationship? = null,
    val relationshipSupported: Boolean? = null,
    val relationshipLoading: Boolean = false,
    val relationshipMutation: Boolean = false,
    val relationshipError: String? = null,
    val pinnedPosts: List<OwnedPost> = emptyList(),
    val pinnedLoading: Boolean = false,
    val pinnedError: String? = null,
    val selectedTab: ProfileCategory = ProfileCategory.Posts,
    val pages: Map<ProfileTimelineTab, ProfilePageState> = emptyMap(),
    /** Whether the Liked tab is offered for the current target on this source. */
    val likedAvailable: Boolean = false,
    val savingProfile: Boolean = false,
    val editError: String? = null,
    /** Whether basic profile editing has any path on this account's source. */
    val editableSupported: Boolean = true,
    val editorOpen: Boolean = false,
    val editableLoading: Boolean = false,
    val editable: EditableProfile? = null,
    val editableError: String? = null,
    val editorCapabilities: EditableProfileCapabilities = EditableProfileCapabilities(),
    /** Working copy of the editable profile. Owned here so the shell holds no editor state. */
    val editorDraft: EditableProfile? = null,
) {
    /** The profile the working copy is compared against and patched from. */
    val editorBase: EditableProfile?
        get() = editable ?: (account ?: seedAccount)?.toEditableProfile()

    /** True when the working copy differs from the loaded base. */
    val editorDirty: Boolean
        get() = editorDraft != null && editorBase != null && editorDraft != editorBase
}

internal fun Account.toEditableProfile(): EditableProfile = EditableProfile(
    id = id.localId,
    displayName = displayName,
    biography = biography,
    fields = profileFields.map { EditableProfileField(it.name, it.value) },
    avatarUrl = avatarUrl,
    headerUrl = bannerUrl,
    locked = locked,
    bot = bot,
)
