package me.foxtails.palustris.domain

data class EmojiCatalogSnapshot(
    val items: List<CustomEmoji>,
    val refreshedAtEpochMillis: Long,
)

interface EmojiCatalogRepository {
    suspend fun read(accountId: AccountId): EmojiCatalogSnapshot?

    suspend fun refresh(
        accountId: AccountId,
        source: SocialSource,
    ): EmojiCatalogSnapshot

    suspend fun remove(accountId: AccountId)
}
