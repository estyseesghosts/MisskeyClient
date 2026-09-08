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
)

data class ProfileField(val name: String, val value: String)

data class UpdateProfileRequest(
    val displayName: String,
    val biography: String,
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
