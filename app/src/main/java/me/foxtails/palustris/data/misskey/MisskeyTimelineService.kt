package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.timelineStatus
import org.json.JSONArray
import org.json.JSONObject

internal class MisskeyTimelineService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
) {
    suspend fun timeline(timeline: Timeline, cursor: String?, capabilities: ServerCapabilities): Page<Post> {
        when (capabilities.timelineStatus(timeline)) {
            CapabilityStatus.Supported -> Unit
            CapabilityStatus.Denied -> throw SourceError.AccessDenied("timeline:$timeline")
            CapabilityStatus.Unsupported -> throw SourceError.Unsupported("timeline:$timeline")
            CapabilityStatus.TemporarilyUnavailable -> throw SourceError.ServerError("timeline:$timeline")
            CapabilityStatus.Unknown -> throw SourceError.Unsupported("timeline:$timeline")
        }
        val params = JSONObject().put("i", token).put("limit", 30)
        if (cursor != null) params.put("untilId", cursor)
        val endpoint = when (timeline) {
            Timeline.Home -> "notes/timeline"
            Timeline.Local -> "notes/local-timeline"
            Timeline.Social -> "notes/hybrid-timeline"
            Timeline.Bubble -> "notes/bubble-timeline"
            Timeline.Federated -> "notes/global-timeline"
        }
        params.put("withFiles", true)
        val notes = JSONArray(api.post(origin, endpoint, params).body)
        return Page(
            (0 until notes.length()).map { MisskeyMapper.post(notes.getJSONObject(it), origin) },
            // Misskey paginates by the outer renote ID, not the displayed original note.
            if (notes.length() > 0) notes.getJSONObject(notes.length() - 1).getString("id") else null,
        )
    }
}
