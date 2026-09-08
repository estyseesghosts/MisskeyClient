package me.foxtails.palustris.domain

import kotlinx.coroutines.flow.Flow

const val DEFAULT_FAVOURITE_EMOJI = "❤️"
const val MAX_FAVOURITE_EMOJI_LENGTH = 64

data class PostPreferences(val favouriteEmoji: String = DEFAULT_FAVOURITE_EMOJI)

fun normalizeFavouriteEmoji(value: String?): String = value
    ?.trim()
    ?.takeIf { it.isNotEmpty() && it.length <= MAX_FAVOURITE_EMOJI_LENGTH && it.none(Char::isISOControl) }
    ?: DEFAULT_FAVOURITE_EMOJI

interface PostPreferencesRepository {
    fun observe(accountId: AccountId): Flow<PostPreferences>
    suspend fun update(accountId: AccountId, transform: (PostPreferences) -> PostPreferences)
    suspend fun remove(accountId: AccountId)
}
