package me.foxtails.palustris.domain

data class Account(
    val id: AccountId,
    val displayName: String,
    val handle: String,
    val avatarUrl: String? = null,
    val biography: String = "",
    val profileFields: List<ProfileField> = emptyList(),
    val bannerUrl: String? = null,
    val followersCount: Long? = null,
    val followingCount: Long? = null,
    val postsCount: Long? = null,
    val locked: Boolean = false,
    val bot: Boolean = false,
    /** A normalized, one-hop destination profile seed for a moved account. */
    val movedTo: Account? = null,
    /** Entity-local custom emoji keyed by display token; never submission identity. */
    val emoji: Map<String, CustomEmoji> = emptyMap(),
)

data class ProfileField(val name: String, val value: String)

data class EditableProfileField(val name: String, val value: String)

/** Raw self-profile values as the server stores them; never rendered through public Account. */
data class EditableProfile(
    val id: String,
    val displayName: String = "",
    val biography: String = "",
    val fields: List<EditableProfileField> = emptyList(),
    val avatarUrl: String? = null,
    val avatarDescription: String? = null,
    val headerUrl: String? = null,
    val headerDescription: String? = null,
    val locked: Boolean = false,
    val bot: Boolean = false,
    val hideCollections: Boolean = false,
    val discoverable: Boolean = false,
    val indexable: Boolean = false,
    val showMedia: Boolean = false,
    val showMediaReplies: Boolean = false,
    val showFeatured: Boolean = false,
    val attributionDomains: List<String> = emptyList(),
)

/** Null means "do not change". Empty string clears text; empty list clears fields/domains. */
data class EditableProfilePatch(
    val displayName: String? = null,
    val biography: String? = null,
    val fields: List<EditableProfileField>? = null,
    val avatarDescription: String? = null,
    val headerDescription: String? = null,
    val locked: Boolean? = null,
    val bot: Boolean? = null,
    val hideCollections: Boolean? = null,
    val discoverable: Boolean? = null,
    val indexable: Boolean? = null,
    val showMedia: Boolean? = null,
    val showMediaReplies: Boolean? = null,
    val showFeatured: Boolean? = null,
    val attributionDomains: List<String>? = null,
) {
    val isEmpty: Boolean
        get() = this == EditableProfilePatch()
}

/** Merges editable public values into the displayed account without touching server-owned identity. */
fun EditableProfile.mergeInto(account: Account): Account = account.copy(
    displayName = displayName.ifBlank { account.displayName },
    biography = biography,
    profileFields = fields.map { ProfileField(it.name, it.value) },
    avatarUrl = avatarUrl ?: account.avatarUrl,
    bannerUrl = headerUrl ?: account.bannerUrl,
    locked = locked,
    bot = bot,
)

enum class ProfileTimelineTab { Posts, Media, Reposts, Replies }

data class ProfileTimelineQuery(
    val profileId: AccountId,
    val tab: ProfileTimelineTab,
)

data class ProfileRelationship(
    val profileId: AccountId,
    val following: Boolean = false,
    val followedBy: Boolean = false,
    val requested: Boolean = false,
    val muting: Boolean = false,
    val blocking: Boolean = false,
)
