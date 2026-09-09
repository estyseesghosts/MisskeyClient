package me.foxtails.palustris.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.PollRequest
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostDraftQuotePreview
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import javax.crypto.SecretKey

interface DraftStore {
    suspend fun list(accountId: AccountId?): List<PostDraft>
    suspend fun save(draft: PostDraft)
    suspend fun delete(accountId: AccountId?, draftId: String)
    suspend fun deleteAll(accountId: AccountId?)
    suspend fun migrateLegacy(accountId: AccountId?, preferences: SharedPreferences)
}

/** Draft payloads use the same encrypted account storage key and never enter the account index. */
class EncryptedDraftStore private constructor(
    context: Context,
    private val accountFiles: AccountFileStore,
) : DraftStore {
    constructor(context: Context) : this(context, AccountFileStore(context))
    internal constructor(context: Context, key: SecretKey) : this(context, AccountFileStore(context, key))

    private val directory = File(context.noBackupFilesDir, "drafts")

    override suspend fun list(accountId: AccountId?): List<PostDraft> = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().mapNotNull { file ->
            runCatching { accountFiles.readJson(file).toDraft() }.getOrNull()
                ?.takeIf { it.accountId == accountId }
        }.sortedByDescending(PostDraft::updatedAt)
    }

    override suspend fun save(draft: PostDraft) = withContext(Dispatchers.IO) {
        require(draft.accountId != null) { "A draft must belong to an account before it can be stored." }
        accountFiles.writeJson(fileFor(draft.accountId, draft.id), draft.toJson())
    }

    override suspend fun delete(accountId: AccountId?, draftId: String) = withContext(Dispatchers.IO) {
        AtomicFile(fileFor(accountId, draftId)).delete()
    }

    override suspend fun deleteAll(accountId: AccountId?) = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().forEach { file ->
            runCatching { accountFiles.readJson(file).toDraft() }
                .getOrNull()?.takeIf { it.accountId == accountId }?.let { AtomicFile(file).delete() }
        }
    }

    override suspend fun migrateLegacy(accountId: AccountId?, preferences: SharedPreferences) {
        if (accountId == null || preferences.getBoolean("migrated_v2", false)) return
        val text = preferences.getString("text", "").orEmpty()
        val warning = preferences.getString("warning", "").orEmpty()
        if (text.isNotBlank() || warning.isNotBlank()) {
            save(PostDraft(accountId = accountId, text = text, contentWarning = warning.takeIf { it.isNotBlank() }))
        }
        preferences.edit().putBoolean("migrated_v2", true).remove("text").remove("warning").apply()
    }

    private fun fileFor(accountId: AccountId?, id: String): File {
        val value = "${accountId?.connection?.origin.orEmpty()}\u0000${accountId?.localId.orEmpty()}\u0000$id"
        val name = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
        return File(directory, "$name.enc")
    }
}

/** Keeps compose-only previews and unit tests deterministic without touching Android Keystore. */
class InMemoryDraftStore : DraftStore {
    private val drafts = linkedMapOf<String, PostDraft>()
    override suspend fun list(accountId: AccountId?): List<PostDraft> = drafts.values.filter { it.accountId == accountId }.sortedByDescending(PostDraft::updatedAt)
    override suspend fun save(draft: PostDraft) { drafts[draft.id] = draft }
    override suspend fun delete(accountId: AccountId?, draftId: String) { drafts.remove(draftId)?.takeIf { it.accountId == accountId } }
    override suspend fun deleteAll(accountId: AccountId?) { drafts.entries.removeIf { it.value.accountId == accountId } }
    override suspend fun migrateLegacy(accountId: AccountId?, preferences: SharedPreferences) {
        if (accountId != null && !preferences.getBoolean("migrated_v2", false)) {
            val text = preferences.getString("text", "").orEmpty()
            val warning = preferences.getString("warning", "").orEmpty()
            if (text.isNotBlank() || warning.isNotBlank()) save(PostDraft(accountId = accountId, text = text, contentWarning = warning.takeIf { it.isNotBlank() }))
            preferences.edit().putBoolean("migrated_v2", true).remove("text").remove("warning").apply()
        }
    }
}

/** Used only when PalustrisApp is rendered directly by previews and UI tests. */
class PreferencesDraftStore(private val preferences: SharedPreferences) : DraftStore {
    private val legacyId = "legacy-local-draft"
    override suspend fun list(accountId: AccountId?): List<PostDraft> {
        val text = preferences.getString("text", "").orEmpty()
        val warning = preferences.getString("warning", "").orEmpty()
        return if (text.isBlank() && warning.isBlank()) emptyList()
        else listOf(PostDraft(legacyId, accountId, text, contentWarning = warning.takeIf { it.isNotBlank() }))
    }
    override suspend fun save(draft: PostDraft) {
        preferences.edit().putString("text", draft.text).putString("warning", draft.contentWarning.orEmpty()).apply()
    }
    override suspend fun delete(accountId: AccountId?, draftId: String) {
        if (draftId == legacyId) preferences.edit().remove("text").remove("warning").apply()
    }
    override suspend fun deleteAll(accountId: AccountId?) = delete(accountId, legacyId)
    override suspend fun migrateLegacy(accountId: AccountId?, preferences: SharedPreferences) = Unit
}

private fun PostDraft.toJson() = JSONObject()
    .put("id", id).put("origin", accountId?.connection?.origin).put("localId", accountId?.localId)
    .put("protocol", accountId?.connection?.protocol?.name).put("text", text).put("audience", audience.name)
    .put("contentWarning", contentWarning).put("replyTo", replyTo?.value).put("quoteOf", quoteOf?.value)
    .put("updatedAt", updatedAt)
    .put("quotePreview", quotePreview?.let {
        JSONObject()
            .put("authorDisplayName", it.authorDisplayName)
            .put("authorHandle", it.authorHandle)
            .put("text", it.text)
            .put("url", it.url)
            .put("authorEmoji", encodeEmojiMap(it.authorEmoji))
            .put("postEmoji", encodeEmojiMap(it.postEmoji))
    })
    .put("attachments", JSONArray(attachments.map { attachment ->
        JSONObject()
            .put("id", attachment.id)
            .put("url", attachment.url)
            .put("mimeType", attachment.mimeType)
            .put("kind", attachment.kind.name)
            .put("description", attachment.description)
            .put("previewUrl", attachment.previewUrl)
            .put("sensitive", attachment.sensitive)
            .put("width", attachment.width)
            .put("height", attachment.height)
            .put("previewWidth", attachment.previewWidth)
            .put("previewHeight", attachment.previewHeight)
            .put("blurhash", attachment.blurhash)
            .put("remoteOriginalUrl", attachment.remoteOriginalUrl)
    }))
    .put("poll", poll?.let { JSONObject().put("choices", JSONArray(it.choices)).put("multiple", it.multiple).put("expiresAt", it.expiresAt?.toEpochMilli()) })

private fun JSONObject.toDraft(): PostDraft {
    val origin = optString("origin").takeIf { it.isNotBlank() }
    val accountId = if (origin == null || isNull("localId") || isNull("protocol")) null else AccountId(me.foxtails.palustris.domain.Connection(origin, me.foxtails.palustris.domain.Protocol.valueOf(getString("protocol"))), getString("localId"))
    val attachments = optJSONArray("attachments")?.let { array ->
        (0 until array.length()).map { index ->
            array.getJSONObject(index).let { json ->
                val mimeType = json.nullableString("mimeType") ?: "application/octet-stream"
                Attachment(
                    id = json.nullableString("id"),
                    url = json.nullableString("url"),
                    mimeType = mimeType,
                    kind = json.optString("kind").takeIf { it.isNotBlank() }?.let { value ->
                        runCatching { MediaKind.valueOf(value) }.getOrNull()
                    } ?: me.foxtails.palustris.domain.mediaKindForMimeType(mimeType),
                    description = json.nullableString("description"),
                    previewUrl = json.nullableString("previewUrl"),
                    sensitive = json.optBoolean("sensitive"),
                    width = json.positiveInt("width"),
                    height = json.positiveInt("height"),
                    previewWidth = json.positiveInt("previewWidth"),
                    previewHeight = json.positiveInt("previewHeight"),
                    blurhash = json.nullableString("blurhash"),
                    remoteOriginalUrl = json.nullableString("remoteOriginalUrl"),
                )
            }
        }
    }.orEmpty()
    val pollJson = optJSONObject("poll")
    val poll = pollJson?.let { PollRequest((0 until it.getJSONArray("choices").length()).map(it.getJSONArray("choices")::getString), it.optBoolean("multiple"), it.optLong("expiresAt").takeIf { value -> value > 0 }?.let(java.time.Instant::ofEpochMilli)) }
    val quotePreview = optJSONObject("quotePreview")?.let {
        PostDraftQuotePreview(
            authorDisplayName = it.optString("authorDisplayName"),
            authorHandle = it.optString("authorHandle"),
            text = it.optString("text"),
            url = it.optString("url").takeIf(String::isNotBlank),
            authorEmoji = decodeEmojiMap(it.optJSONObject("authorEmoji")),
            postEmoji = decodeEmojiMap(it.optJSONObject("postEmoji")),
        )
    }?.takeIf { it.authorDisplayName.isNotBlank() || it.authorHandle.isNotBlank() || it.text.isNotBlank() }
    return PostDraft(getString("id"), accountId, optString("text"), Audience.valueOf(optString("audience", Audience.Public.name)), optString("contentWarning").takeIf(String::isNotBlank), optString("replyTo").takeIf(String::isNotBlank)?.let { EntityId(origin.orEmpty(), it) }, optString("quoteOf").takeIf(String::isNotBlank)?.let { EntityId(origin.orEmpty(), it) }, attachments, poll, optLong("updatedAt"), quotePreview)
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

private fun encodeEmojiMap(emoji: Map<String, me.foxtails.palustris.domain.CustomEmoji>): JSONObject = JSONObject().apply {
    emoji.forEach { (key, value) ->
        put(key, JSONObject()
            .put("shortcode", value.shortcode)
            .put("animatedUrl", value.animatedUrl?.value)
            .put("staticUrl", value.staticUrl?.value)
            .put("category", value.category)
            .put("aliases", JSONArray(value.aliases))
            .put("visibleInPicker", value.visibleInPicker)
            .put("submissionValue", value.submissionValue))
    }
}

private fun decodeEmojiMap(json: JSONObject?): Map<String, me.foxtails.palustris.domain.CustomEmoji> {
    if (json == null) return emptyMap()
    val result = linkedMapOf<String, me.foxtails.palustris.domain.CustomEmoji>()
    json.keys().asSequence().forEach { key ->
        runCatching {
            val entry = json.getJSONObject(key)
            entry.optString("shortcode").takeIf { it.isNotBlank() }?.let { shortcode ->
                val submissionValue = entry.optString("submissionValue").takeIf { it.isNotBlank() } ?: ":$shortcode:"
                me.foxtails.palustris.domain.CustomEmoji(
                    shortcode = shortcode,
                    animatedUrl = entry.optString("animatedUrl").takeIf { it.isNotBlank() }
                        ?.let(me.foxtails.palustris.domain.ValidatedUrl::https),
                    staticUrl = entry.optString("staticUrl").takeIf { it.isNotBlank() }
                        ?.let(me.foxtails.palustris.domain.ValidatedUrl::https),
                    category = entry.optString("category").takeIf { it.isNotBlank() },
                    aliases = entry.optJSONArray("aliases")?.let { values ->
                        (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                    }.orEmpty(),
                    visibleInPicker = entry.optBoolean("visibleInPicker", true),
                    submissionValue = submissionValue,
                )
            }
        }.getOrNull()?.let { result[key] = it }
    }
    return result
}

private fun JSONObject.positiveInt(key: String): Int? = when (val value = opt(key)) {
    is Number -> value.toInt().takeIf { it > 0 }
    is String -> value.toIntOrNull()?.takeIf { it > 0 }
    else -> null
}
