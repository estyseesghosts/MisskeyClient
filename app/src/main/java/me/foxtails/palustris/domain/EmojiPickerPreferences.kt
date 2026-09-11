package me.foxtails.palustris.domain

data class EmojiPickerPreferences(
    val collapsedGroups: Set<String> = emptySet(),
    val pinnedGroups: List<String> = emptyList(),
)

object EmojiPickerGroupIds {
    const val Favorite = "favorite"
    const val Recent = "recent"
    const val Unicode = "unicode"
    const val ServerPrefix = "server:"
    const val PostSpecific = "post-specific"

    fun server(category: String?): String = "$ServerPrefix${category.orEmpty()}"

    fun isServer(value: String): Boolean = value.startsWith(ServerPrefix) && value.length > ServerPrefix.length || value == ServerPrefix
}

interface EmojiPickerPreferencesRepository {
    fun observe(accountId: AccountId): kotlinx.coroutines.flow.Flow<EmojiPickerPreferences>

    suspend fun update(
        accountId: AccountId,
        transform: (EmojiPickerPreferences) -> EmojiPickerPreferences,
    )

    suspend fun remove(accountId: AccountId)
}
