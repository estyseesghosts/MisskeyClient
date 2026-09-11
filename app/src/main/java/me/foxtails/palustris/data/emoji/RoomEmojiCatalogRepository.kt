package me.foxtails.palustris.data.emoji

import androidx.annotation.VisibleForTesting
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.foxtails.palustris.di.IoDispatcher
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiCatalogRepository
import me.foxtails.palustris.domain.EmojiCatalogSnapshot
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONArray

@Singleton
class RoomEmojiCatalogRepository @Inject constructor(
    private val database: EmojiCacheDatabase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : EmojiCatalogRepository {
    private val dao = database.emojiCacheDao()

    override suspend fun read(accountId: AccountId): EmojiCatalogSnapshot? = withContext(ioDispatcher) {
        val key = accountId.emojiAccountKey()
        val snapshot = dao.catalogSnapshot(key) ?: return@withContext null
        EmojiCatalogSnapshot(
            items = dao.catalogEntries(key).mapNotNull(::toDomain),
            refreshedAtEpochMillis = snapshot.refreshedAtEpochMillis,
        )
    }

    override suspend fun refresh(
        accountId: AccountId,
        source: SocialSource,
    ): EmojiCatalogSnapshot {
        val items = source.customEmojis()
        val refreshedAt = System.currentTimeMillis()
        val key = accountId.emojiAccountKey()
        withContext(ioDispatcher) {
            dao.replaceCatalog(
                accountKey = key,
                refreshedAtEpochMillis = refreshedAt,
                entries = items.mapIndexed { position, emoji -> emoji.toEntity(key, position) },
            )
        }
        return EmojiCatalogSnapshot(items, refreshedAt)
    }

    override suspend fun remove(accountId: AccountId) = withContext(ioDispatcher) {
        dao.deleteAccountCatalog(accountId.emojiAccountKey())
    }

    private fun toDomain(entity: EmojiCatalogEntryEntity): CustomEmoji? = runCatching {
        CustomEmoji(
            shortcode = entity.shortcode,
            animatedUrl = entity.animatedUrl?.let(ValidatedUrl::https),
            staticUrl = entity.staticUrl?.let(ValidatedUrl::https),
            category = entity.category,
            aliases = JSONArray(entity.aliasesJson).let { aliases ->
                (0 until aliases.length()).mapNotNull { aliases.optString(it).takeIf(String::isNotBlank) }
            },
            visibleInPicker = entity.visibleInPicker,
            submissionValue = entity.emojiIdentity,
        )
    }.getOrNull()

    @VisibleForTesting
    internal fun accountKey(accountId: AccountId): String = accountId.emojiAccountKey()
}

private fun CustomEmoji.toEntity(accountKey: String, position: Int) = EmojiCatalogEntryEntity(
    accountKey = accountKey,
    emojiIdentity = submissionValue,
    shortcode = shortcode,
    animatedUrl = animatedUrl?.value,
    staticUrl = staticUrl?.value,
    category = category,
    aliasesJson = JSONArray(aliases).toString(),
    visibleInPicker = visibleInPicker,
    catalogPosition = position,
)

class InMemoryEmojiCatalogRepository : EmojiCatalogRepository {
    private val snapshots = mutableMapOf<AccountId, EmojiCatalogSnapshot>()

    override suspend fun read(accountId: AccountId): EmojiCatalogSnapshot? = snapshots[accountId]

    override suspend fun refresh(
        accountId: AccountId,
        source: SocialSource,
    ): EmojiCatalogSnapshot {
        val snapshot = EmojiCatalogSnapshot(source.customEmojis(), System.currentTimeMillis())
        snapshots[accountId] = snapshot
        return snapshot
    }

    override suspend fun remove(accountId: AccountId) {
        snapshots.remove(accountId)
    }
}
