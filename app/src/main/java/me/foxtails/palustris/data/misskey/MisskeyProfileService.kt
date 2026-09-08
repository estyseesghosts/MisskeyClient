package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.CancellationException
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.matchesProfileTimeline
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Account-detail and profile-feed operations for one authenticated Misskey session. */
class MisskeyProfileService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val authenticatedAccountId: AccountId?,
) {
    suspend fun profile(id: AccountId): Account {
        validateTarget(id, "profile.details")
        return showProfile(id)
    }

    suspend fun timeline(query: ProfileTimelineQuery, cursor: String? = null): Page<Post> {
        validateTarget(query.profileId, "profile.timeline")
        val body = JSONObject()
            .put("i", token)
            .put("userId", query.profileId.localId)
            .put("limit", PROFILE_PAGE_SIZE)
        cursor?.let { body.put("untilId", it) }
        when (query.tab) {
            ProfileTimelineTab.Posts -> body.put("withReplies", false).put("withRenotes", false)
            ProfileTimelineTab.Media -> body.put("withFiles", true).put("withRenotes", false)
            ProfileTimelineTab.Reposts -> body.put("withReplies", false).put("withRenotes", true)
            ProfileTimelineTab.Replies -> body.put("withReplies", true).put("withRenotes", false)
        }
        val notes = JSONArray(api.post(origin, "users/notes", body).body)
        val nextCursor = notes.optJSONObject(notes.length() - 1)?.optString("id")
            ?.takeIf { it.isNotBlank() }
        val items = (0 until notes.length())
            .map { MisskeyMapper.post(notes.getJSONObject(it), origin) }
            .filter { it.matchesProfileTimeline(query) }
        return Page(items = items, nextCursor = nextCursor)
    }

    suspend fun relationship(id: AccountId): ProfileRelationship {
        validateTarget(id, "profile.relationship")
        val response = api.post(
            origin,
            "users/relation",
            JSONObject().put("i", token).put("userId", id.localId),
        )
        return parseRelationship(response.body, id)
    }

    suspend fun follow(id: AccountId): ProfileRelationship {
        validateTarget(id, "profile.follow")
        api.post(origin, "following/create", JSONObject().put("i", token).put("userId", id.localId))
        return relationship(id)
    }

    suspend fun unfollow(id: AccountId): ProfileRelationship {
        validateTarget(id, "profile.unfollow")
        api.post(origin, "following/delete", JSONObject().put("i", token).put("userId", id.localId))
        return relationship(id)
    }

    suspend fun pinnedPosts(id: AccountId): List<Post> {
        validateTarget(id, "profile.pinned")
        val profile = try {
            JSONObject(api.post(origin, "users/show", JSONObject().put("i", token).put("userId", id.localId)).body)
        } catch (error: ApiFailure) {
            if (error.status in setOf(400, 404, 422)) return emptyList()
            throw error
        }
        val pinnedNotes = profile.optJSONArray("pinnedNotes")
        val inlinePosts = pinnedNotes?.let { notes ->
            (0 until notes.length()).mapNotNull { index ->
                val note = notes.optJSONObject(index) ?: return@mapNotNull null
                runCatching { MisskeyMapper.post(note, origin) }.getOrNull()
            }.filter { it.author.id == id }.takeIf { it.isNotEmpty() || notes.length() == 0 }
        }
        if (inlinePosts != null) {
            return inlinePosts
        }
        val noteIds = pinnedNotes?.let { notes ->
            (0 until notes.length()).mapNotNull { notes.optString(it).takeIf(String::isNotBlank) }
        }?.takeIf { it.isNotEmpty() }
            ?: profile.optJSONArray("pinnedNoteIds")?.let { ids ->
                (0 until ids.length()).mapNotNull { ids.optString(it).takeIf(String::isNotBlank) }
            }
            ?: return emptyList()
        return noteIds.take(MAX_PINNED_NOTES).mapNotNull { noteId ->
            runCatching {
                val response = api.post(origin, "notes/show", JSONObject().put("i", token).put("noteId", noteId))
                MisskeyMapper.post(JSONObject(response.body), origin)
            }.getOrNull()
        }.filter { it.author.id == id }
    }

    private suspend fun showProfile(id: AccountId): Account {
        val profile = JSONObject(api.post(
            origin,
            "users/show",
            JSONObject().put("i", token).put("userId", id.localId),
        ).body)
        val account = MisskeyMapper.account(profile, origin)
        val movedTo = profile.nullableString("movedTo")?.let { resolveMovedTo(it) }
        return account.copy(movedTo = movedTo)
    }

    private suspend fun resolveMovedTo(value: String): Account? {
        return try {
            val destination = if (value.toHttpUrlOrNull() != null) {
                val response = api.post(
                    origin,
                    "ap/show",
                    JSONObject().put("i", token).put("uri", value),
                )
                val envelope = JSONObject(response.body)
                if (envelope.optString("type") != "User") return null
                envelope.optJSONObject("object")?.let { MisskeyMapper.account(it, origin, movedTo = null) }
            } else {
                val response = api.post(
                    origin,
                    "users/show",
                    JSONObject().put("i", token).put("userId", value),
                )
                MisskeyMapper.account(JSONObject(response.body), origin, movedTo = null)
            }
            destination?.takeIf {
                it.id.localId.isNotBlank() &&
                    (it.displayName.isNotBlank() || it.handle.trim('@').isNotBlank() || it.avatarUrl != null)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun validateTarget(id: AccountId, feature: String) {
        if (id.connection.origin != origin || id.localId.isBlank() ||
            authenticatedAccountId?.connection?.origin?.let { it != origin } == true
        ) {
            throw SourceError.Unsupported(feature)
        }
    }

    private fun parseRelationship(body: String, profileId: AccountId): ProfileRelationship {
        val root = body.trimStart()
        val json = if (root.startsWith("[")) {
            JSONArray(body).optJSONObject(0)
        } else {
            JSONObject(body)
        }?.let {
            it.optJSONObject("relation") ?: it.optJSONObject("relationship") ?: it
        }
        if (json == null || (!json.has("isFollowing") && !json.has("following") &&
            !json.has("isFollowed") && !json.has("followedBy") &&
            !json.has("hasPendingRequestFromYou") && !json.has("requested"))
        ) {
            throw SourceError.Unsupported("profile.relationship")
        }
        return MisskeyMapper.relationship(json, profileId)
    }

    private companion object {
        const val PROFILE_PAGE_SIZE = 40
        const val MAX_PINNED_NOTES = 20
    }
}
