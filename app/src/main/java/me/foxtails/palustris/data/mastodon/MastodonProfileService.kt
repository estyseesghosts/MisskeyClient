package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.matchesProfileTimeline
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Account-detail and profile-feed operations for one authenticated Mastodon session. */
class MastodonProfileService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val authenticatedAccountId: AccountId,
) {
    suspend fun profile(id: AccountId): Account {
        validateTarget(id, "profile.details")
        return MastodonMapper.account(api.getUrl(accountUrl(id).toString(), token).body.toJson(), origin)
    }

    suspend fun timeline(query: ProfileTimelineQuery, cursor: String? = null): Page<Post> {
        validateTarget(query.profileId, "profile.timeline")
        val response = if (cursor == null) {
            api.getUrl(timelineUrl(query.profileId, query).toString(), token)
        } else {
            api.getUrl(validatePaginationUrl(cursor, query.profileId).toString(), token)
        }
        val statuses = JSONArray(response.body)
        val items = (0 until statuses.length())
            .map { MastodonMapper.post(statuses.getJSONObject(it), origin) }
            .filter { it.matchesProfileTimeline(query) }
        return Page(items = items, nextCursor = response.linkHeaderCursor())
    }

    suspend fun relationship(id: AccountId): ProfileRelationship {
        validateTarget(id, "profile.relationship")
        val response = api.getUrl(relationshipUrl(id).toString(), token)
        return parseRelationship(response.body, id)
    }

    suspend fun follow(id: AccountId): ProfileRelationship = mutateRelationship(id, "follow")

    suspend fun unfollow(id: AccountId): ProfileRelationship = mutateRelationship(id, "unfollow")

    suspend fun pinnedPosts(id: AccountId): List<Post> {
        validateTarget(id, "profile.pinned")
        val response = try {
            api.getUrl(pinnedUrl(id).toString(), token)
        } catch (error: ApiFailure) {
            if (error.status in setOf(400, 404, 422)) return emptyList()
            throw error
        }
        val statuses = JSONArray(response.body)
        return (0 until statuses.length())
            .map { MastodonMapper.post(statuses.getJSONObject(it), origin) }
            .filter { it.author.id == id }
    }

    private suspend fun mutateRelationship(id: AccountId, action: String): ProfileRelationship {
        validateTarget(id, "profile.$action")
        val response = api.postForm(actionUrl(id, action), emptyList(), token)
        return runCatching { parseRelationship(response.body, id) }
            .getOrElse { relationship(id) }
    }

    private fun validateTarget(id: AccountId, feature: String) {
        if (id.connection.origin != origin || id.localId.isBlank() ||
            authenticatedAccountId.connection.origin != origin
        ) {
            throw SourceError.Unsupported(feature)
        }
    }

    private fun validatePaginationUrl(cursor: String, profileId: AccountId): HttpUrl {
        val page = cursor.toHttpUrlOrNull() ?: throw SourceError.Unsupported("profile.pagination")
        val authenticatedOrigin = origin.toHttpUrl()
        val expectedPath = timelinePath(profileId).encodedPath
        if (page.scheme != authenticatedOrigin.scheme || page.host != authenticatedOrigin.host ||
            page.port != authenticatedOrigin.port || page.username.isNotEmpty() ||
            page.password.isNotEmpty() || page.fragment != null || page.encodedPath != expectedPath
        ) {
            throw SourceError.Unsupported("profile.pagination")
        }
        return page
    }

    private fun accountUrl(id: AccountId): HttpUrl = origin.toHttpUrl().newBuilder()
        .addPathSegment("api")
        .addPathSegment("v1")
        .addPathSegment("accounts")
        .addPathSegment(id.localId)
        .build()

    private fun timelinePath(id: AccountId): HttpUrl = accountUrl(id).newBuilder()
        .addPathSegment("statuses")
        .build()

    private fun timelineUrl(id: AccountId, query: ProfileTimelineQuery): HttpUrl =
        timelinePath(id).newBuilder()
            .addQueryParameter("limit", PROFILE_PAGE_SIZE.toString())
            .apply {
                when (query.tab) {
                    ProfileTimelineTab.Posts -> {
                        addQueryParameter("exclude_replies", "true")
                        addQueryParameter("exclude_reblogs", "true")
                    }
                    ProfileTimelineTab.Media -> {
                        addQueryParameter("only_media", "true")
                        addQueryParameter("exclude_reblogs", "true")
                    }
                    ProfileTimelineTab.Reposts -> {
                        addQueryParameter("exclude_replies", "true")
                        addQueryParameter("exclude_reblogs", "false")
                    }
                    ProfileTimelineTab.Replies -> {
                        addQueryParameter("exclude_replies", "false")
                        addQueryParameter("exclude_reblogs", "true")
                    }
                }
            }
            .build()

    private fun relationshipUrl(id: AccountId): HttpUrl = origin.toHttpUrl().newBuilder()
        .addPathSegment("api")
        .addPathSegment("v1")
        .addPathSegment("accounts")
        .addPathSegment("relationships")
        .addQueryParameter("id[]", id.localId)
        .build()

    private fun actionUrl(id: AccountId, action: String): HttpUrl = accountUrl(id).newBuilder()
        .addPathSegment(action)
        .build()

    private fun pinnedUrl(id: AccountId): HttpUrl = accountUrl(id).newBuilder()
        .addPathSegment("statuses")
        .addQueryParameter("pinned", "true")
        .addQueryParameter("limit", PROFILE_PAGE_SIZE.toString())
        .build()

    private fun parseRelationship(body: String, profileId: AccountId): ProfileRelationship {
        val trimmed = body.trimStart()
        val json = if (trimmed.startsWith("[")) {
            JSONArray(body).optJSONObject(0)
        } else {
            JSONObject(body)
        }
        if (json == null || !json.has("following") && !json.has("requested") && !json.has("followed_by")) {
            throw SourceError.Unsupported("profile.relationship")
        }
        return MastodonMapper.relationship(json, profileId)
    }

    private companion object {
        const val PROFILE_PAGE_SIZE = 40
    }
}

private fun String.toJson(): JSONObject = JSONObject(this)
