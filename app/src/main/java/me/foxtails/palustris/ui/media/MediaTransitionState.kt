package me.foxtails.palustris.ui.media

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import me.foxtails.palustris.domain.OwnedPost

/** Identifies one media attachment across feed recompositions and account sessions. */
data class MediaTransitionKey(
    val account: String,
    val post: String,
    val attachment: String,
) {
    companion object {
        fun forAttachment(ownedPost: OwnedPost, attachmentIndex: Int): MediaTransitionKey {
            val attachment = ownedPost.post.attachments.getOrNull(attachmentIndex)
            return MediaTransitionKey(
                account = ownedPost.fetchedBy.toString(),
                post = "${ownedPost.post.id.connection}/${ownedPost.post.id.value}",
                attachment = attachment?.id ?: attachmentIndex.toString(),
            )
        }
    }
}

/** A shared root-coordinate registry used by the feed and media overlay. */
class MediaTransitionRegistry {
    private val bounds = mutableStateMapOf<MediaTransitionKey, Rect>()
    private val hiddenSources = mutableStateMapOf<MediaTransitionKey, Boolean>()
    private val activeKey = mutableStateOf<MediaTransitionKey?>(null)

    val currentActiveKey: MediaTransitionKey?
        get() = activeKey.value

    fun update(key: MediaTransitionKey, value: Rect) {
        if (value.width > 0f && value.height > 0f) bounds[key] = value
    }

    fun remove(key: MediaTransitionKey) {
        bounds.remove(key)
    }

    fun boundsFor(key: MediaTransitionKey): Rect? = bounds[key]

    fun begin(key: MediaTransitionKey) {
        activeKey.value = key
        hiddenSources[key] = false
    }

    fun end(key: MediaTransitionKey) {
        hiddenSources.remove(key)
        if (activeKey.value == key) activeKey.value = null
    }

    fun endActive() {
        activeKey.value?.let(hiddenSources::remove)
        activeKey.value = null
    }

    fun isActive(key: MediaTransitionKey): Boolean = activeKey.value == key

    fun markSourceReady(key: MediaTransitionKey) {
        if (activeKey.value == key) hiddenSources[key] = true
    }

    fun isSourceHidden(key: MediaTransitionKey): Boolean = hiddenSources[key] == true
}

val LocalMediaTransitionRegistry = androidx.compose.runtime.compositionLocalOf { MediaTransitionRegistry() }

fun fitRect(container: Rect, imageWidth: Float, imageHeight: Float): Rect {
    if (container.isEmpty || imageWidth <= 0f || imageHeight <= 0f) return container
    val scale = minOf(container.width / imageWidth, container.height / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return Rect(
        left = container.left + (container.width - width) / 2f,
        top = container.top + (container.height - height) / 2f,
        right = container.left + (container.width + width) / 2f,
        bottom = container.top + (container.height + height) / 2f,
    )
}

fun lerpRect(start: Rect, end: Rect, fraction: Float): Rect = lerp(start, end, fraction.coerceIn(0f, 1f))

fun dismissProgress(drag: Float, dismissDistance: Float): Float =
    if (dismissDistance <= 0f) 1f else (kotlin.math.abs(drag) / dismissDistance).coerceIn(0f, 1f)

fun shouldDismiss(drag: Float, velocity: Float, viewportHeight: Float): Boolean {
    val distanceThreshold = viewportHeight * 0.20f
    return kotlin.math.abs(drag) >= distanceThreshold || kotlin.math.abs(velocity) >= 1_400f
}
