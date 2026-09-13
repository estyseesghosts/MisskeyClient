package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import org.json.JSONArray

internal class MastodonTimelineService(
    private val pageClient: MastodonPageClient,
    private val origin: String,
) {
    suspend fun timeline(
        timeline: Timeline,
        cursor: String?,
        capabilities: ServerCapabilities,
    ): Page<Post> {
        if (timeline !in capabilities.timelines) throw SourceError.Unsupported("timeline:$timeline")
        val endpoint = when (timeline) {
            Timeline.Home -> "v1/timelines/home"
            Timeline.Local -> "v1/timelines/public?local=true"
            Timeline.Federated -> "v1/timelines/public"
            Timeline.Social, Timeline.Bubble -> throw SourceError.Unsupported("timeline:$timeline")
        }
        val response = pageClient.getPage(endpoint, cursor)
        val statuses = JSONArray(response.body)
        return Page(
            items = (0 until statuses.length()).map { MastodonMapper.post(statuses.getJSONObject(it), origin) },
            nextCursor = response.linkHeaderCursor(),
        )
    }
}
