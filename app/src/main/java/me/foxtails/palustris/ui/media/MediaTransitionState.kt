package me.foxtails.palustris.ui.media

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import coil.request.ImageRequest
import me.foxtails.palustris.domain.OwnedPost

/** Identifies one media attachment across feed recompositions and account sessions. */
data class MediaTransitionKey(
    val account: String,
    val post: String,
    val attachment: String,
    val occurrence: String = "default",
) {
    companion object {
        fun forAttachment(
            ownedPost: OwnedPost,
            attachmentIndex: Int,
            occurrence: String = "default",
        ): MediaTransitionKey {
            val attachment = ownedPost.post.attachments.getOrNull(attachmentIndex)
            return MediaTransitionKey(
                account = ownedPost.fetchedBy.toString(),
                post = "${ownedPost.post.id.connection}/${ownedPost.post.id.value}",
                attachment = attachment?.id ?: attachmentIndex.toString(),
                occurrence = occurrence,
            )
        }
    }
}

/** The source content and clipping that are currently visible in the feed. */
data class MediaTransitionSource(
    val fullBounds: Rect,
    val visibleBounds: Rect = fullBounds,
    val cornerRadiusPx: Float = 0f,
    val previewRequest: ImageRequest? = null,
    val imageWidth: Float = fullBounds.width.coerceAtLeast(1f),
    val imageHeight: Float = fullBounds.height.coerceAtLeast(1f),
)

/** Ownership token for one mounted viewer instance. */
class MediaTransitionOwner internal constructor(
    val key: MediaTransitionKey,
    val id: Long,
)

/** A transition frame separates image placement from the clip that reveals it. */
data class MediaTransitionFrame(
    val imageBounds: Rect,
    val clipBounds: Rect,
    val visibleBounds: Rect = clipBounds,
    val cornerRadiusPx: Float = 0f,
)

/** A shared root-coordinate registry used by the feed and media overlay. */
class MediaTransitionRegistry {
    private val sources = mutableStateMapOf<MediaTransitionKey, MediaTransitionSource>()
    private val hiddenSources = mutableStateMapOf<MediaTransitionKey, Boolean>()
    private val activeKey = mutableStateOf<MediaTransitionKey?>(null)
    private val activeOwner = mutableStateOf<MediaTransitionOwner?>(null)
    private var nextOwnerId = 0L

    val currentActiveKey: MediaTransitionKey?
        get() = activeKey.value

    fun update(key: MediaTransitionKey, value: Rect) {
        if (value.width > 0f && value.height > 0f) {
            val previous = sources[key]
            val next = MediaTransitionSource(
                fullBounds = value,
                visibleBounds = value,
                cornerRadiusPx = previous?.cornerRadiusPx ?: 0f,
                previewRequest = previous?.previewRequest,
                imageWidth = previous?.imageWidth ?: value.width,
                imageHeight = previous?.imageHeight ?: value.height,
            )
            if (previous == null || !previous.matches(next)) sources[key] = next
        }
    }

    fun update(key: MediaTransitionKey, value: MediaTransitionSource) {
        if (value.fullBounds.isValid() && value.visibleBounds.isValid()) {
            val previous = sources[key]
            if (previous == null || !previous.matches(value)) sources[key] = value
        }
    }

    fun remove(key: MediaTransitionKey) {
        sources.remove(key)
    }

    fun boundsFor(key: MediaTransitionKey): Rect? = sources[key]?.fullBounds

    fun sourceFor(key: MediaTransitionKey): MediaTransitionSource? = sources[key]

    fun begin(key: MediaTransitionKey): MediaTransitionOwner {
        activeKey.value?.takeIf { it != key }?.let { hiddenSources[it] = false }
        val owner = MediaTransitionOwner(key, ++nextOwnerId)
        activeOwner.value = owner
        activeKey.value = key
        hiddenSources[key] = true
        return owner
    }

    fun end(key: MediaTransitionKey) {
        hiddenSources.remove(key)
        if (activeOwner.value?.key == key) {
            activeOwner.value = null
            activeKey.value = null
        }
    }

    fun end(owner: MediaTransitionOwner) {
        if (activeOwner.value != owner) return
        hiddenSources.remove(owner.key)
        activeOwner.value = null
        activeKey.value = null
    }

    fun endActive() {
        activeOwner.value?.let(::end)
    }

    fun isActive(key: MediaTransitionKey): Boolean = activeKey.value == key

    fun markSourceReady(key: MediaTransitionKey) {
        if (activeKey.value == key) hiddenSources[key] = true
    }

    fun prepareHandoff(owner: MediaTransitionOwner) {
        if (activeOwner.value == owner) hiddenSources[owner.key] = false
    }

    fun isOwnerActive(owner: MediaTransitionOwner): Boolean = activeOwner.value == owner

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

fun lerpRect(start: Rect, end: Rect, fraction: Float): Rect = lerp(start, end, fraction)

fun lerpFrame(start: MediaTransitionFrame, end: MediaTransitionFrame, fraction: Float): MediaTransitionFrame =
    MediaTransitionFrame(
        imageBounds = lerpRect(start.imageBounds, end.imageBounds, fraction),
        clipBounds = lerpRect(start.clipBounds, end.clipBounds, fraction),
        visibleBounds = lerpRect(start.visibleBounds, end.visibleBounds, fraction),
        cornerRadiusPx = start.cornerRadiusPx + (end.cornerRadiusPx - start.cornerRadiusPx) * fraction,
    )

fun dismissProgress(drag: Float, dismissDistance: Float): Float =
    if (dismissDistance <= 0f) 1f else (kotlin.math.abs(drag) / dismissDistance).coerceIn(0f, 1f)

fun dismissProgress(drag: androidx.compose.ui.geometry.Offset, dismissDistance: Float): Float =
    if (dismissDistance <= 0f) 1f else (drag.getDistance() / dismissDistance).coerceIn(0f, 1f)

fun shouldDismiss(drag: Float, velocity: Float, viewportHeight: Float): Boolean {
    val distanceThreshold = viewportHeight * 0.20f
    return kotlin.math.abs(drag) >= distanceThreshold || kotlin.math.abs(velocity) >= 1_400f
}

fun shouldDismiss(
    drag: androidx.compose.ui.geometry.Offset,
    velocity: androidx.compose.ui.geometry.Offset,
    viewport: Rect,
    velocityThresholdPx: Float,
): Boolean {
    val distanceThreshold = minOf(viewport.width, viewport.height) * 0.20f
    val movedFarEnough = drag.getDistance() >= distanceThreshold
    val velocityAlongDrag = drag.x * velocity.x + drag.y * velocity.y
    val fastEnough = velocity.getDistance() >= velocityThresholdPx &&
        (drag.getDistance() <= 1f || velocityAlongDrag >= 0f)
    return movedFarEnough || fastEnough
}

private fun Rect.isValid(): Boolean = width > 0f && height > 0f

private fun MediaTransitionSource.matches(other: MediaTransitionSource): Boolean =
    fullBounds == other.fullBounds &&
        visibleBounds == other.visibleBounds &&
        cornerRadiusPx == other.cornerRadiusPx &&
        previewRequest === other.previewRequest &&
        imageWidth == other.imageWidth &&
        imageHeight == other.imageHeight
