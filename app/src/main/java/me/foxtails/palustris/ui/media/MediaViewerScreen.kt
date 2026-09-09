@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.openExternal
import me.foxtails.palustris.ui.sharePost

@Composable
fun MediaViewerScreen(
    request: MediaOpenRequest,
    onClose: () -> Unit,
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
) {
    val attachments = request.ownedPost.post.attachments
    if (attachments.isEmpty()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val registry = LocalMediaTransitionRegistry.current
    val motionScheme = LocalPalustrisMotionScheme.current
    val pagerState = rememberPagerState(request.attachmentIndex.coerceIn(0, attachments.lastIndex)) { attachments.size }
    val revealedPages = remember {
        mutableStateMapOf<Int, Boolean>().apply { if (request.revealed) put(request.attachmentIndex, true) }
    }
    var menuVisible by rememberSaveable { mutableStateOf(false) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var descriptionVisible by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val zoomStates = remember(request.transitionKey) { mutableStateMapOf<Int, ZoomableMediaState>() }
    val selectedZoomScale = zoomStates[pagerState.currentPage]?.scale ?: 1f

    DisposableEffect(request.transitionKey) {
        registry.begin(request.transitionKey)
        onDispose { registry.end(request.transitionKey) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Media viewer" },
    ) {
        val viewport = with(density) { Rect(0f, 0f, maxWidth.toPx(), maxHeight.toPx()) }
        val initialAttachment = attachments[request.attachmentIndex.coerceIn(0, attachments.lastIndex)]
        val transition = remember(request.transitionKey) {
            MediaViewerTransitionState(
                initialSourceBounds = request.initialSourceBounds,
                destinationBounds = fitRect(viewport, initialAttachment.imageWidth(), initialAttachment.imageHeight()),
                motionScheme = motionScheme,
            )
        }
        val selectedAttachment = attachments[pagerState.currentPage]
        val selectedDestination = fitRect(viewport, selectedAttachment.imageWidth(), selectedAttachment.imageHeight())
        transition.updateDestinationBounds(selectedDestination)

        fun currentTargetBounds(): Rect? = registry.boundsFor(
            MediaTransitionKey.forAttachment(request.ownedPost, pagerState.currentPage),
        )

        fun requestClose() {
            if (transition.isClosing) return
            scope.launch {
                transition.updateSourceBounds(currentTargetBounds())
                transition.close(currentTargetBounds(), viewport)
                onClose()
            }
        }

        LaunchedEffect(transition) {
            transition.updateSourceBounds(registry.boundsFor(request.transitionKey))
            transition.startOpening()
        }

        BackHandler {
            if (descriptionVisible) descriptionVisible = false else requestClose()
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = transition.backgroundAlpha)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(request.transitionKey, transition.phase, selectedZoomScale) {
                        awaitEachGesture {
                            if (descriptionVisible || selectedZoomScale > 1.01f || transition.isClosing) return@awaitEachGesture
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var previous = down.position
                            var accepted = false
                            while (!accepted) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.size != 1) return@awaitEachGesture
                                val change = event.changes[0]
                                if (change.changedToUpIgnoreConsumed()) return@awaitEachGesture
                                val delta = change.position - previous
                                previous = change.position
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val total = change.position - down.position
                                if (total.getDistance() >= viewConfiguration.touchSlop) {
                                    if (kotlin.math.abs(total.y) > kotlin.math.abs(total.x) * 1.15f) {
                                        accepted = true
                                        change.consume()
                                        transition.beginDrag()
                                        transition.dragBy(total)
                                    } else {
                                        return@awaitEachGesture
                                    }
                                }
                            }
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.size != 1) return@awaitEachGesture
                                val change = event.changes[0]
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (change.changedToUpIgnoreConsumed()) {
                                    val velocityY = tracker.calculateVelocity().y
                                    if (transition.shouldDismiss(velocityY)) {
                                        scope.launch {
                                            transition.updateSourceBounds(currentTargetBounds())
                                            transition.close(currentTargetBounds(), viewport)
                                            onClose()
                                        }
                                    } else {
                                        scope.launch { transition.returnToOpen() }
                                    }
                                    return@awaitEachGesture
                                }
                                transition.dragBy(change.positionChange())
                                change.consume()
                            }
                        }
                    },
                beyondViewportPageCount = 1,
                userScrollEnabled = selectedZoomScale <= 1.01f && !transition.isDismissGestureActive && !transition.isClosing,
            ) { page ->
                val attachment = attachments[page]
                val zoomState = zoomStates.getOrPut(page) { ZoomableMediaState() }
                val selected = page == pagerState.currentPage
                val pageModifier = if (selected) Modifier.transitionTransform(transition.visualBounds, transition.destinationBounds) else Modifier
                MediaPage(
                    attachment = attachment,
                    index = page,
                    selected = selected,
                    revealed = !attachment.sensitive || revealedPages[page] == true,
                    accountIdentity = request.ownedPost.fetchedBy.toString(),
                    postIdentity = "${request.ownedPost.post.id.connection}/${request.ownedPost.post.id.value}",
                    onReveal = { revealedPages[page] = true },
                    zoomState = zoomState,
                    modifier = pageModifier,
                )
                zoomStates[page] = zoomState
            }

            if (chromeVisible) {
                MediaViewerChrome(
                    page = pagerState.currentPage,
                    pageCount = attachments.size,
                    descriptionAvailable = attachments[pagerState.currentPage].description != null,
                    menuVisible = menuVisible,
                    onMenuVisibilityChanged = { menuVisible = it },
                    onClose = ::requestClose,
                    onOpenBrowser = { openExternal(context, attachments[pagerState.currentPage].url) },
                    onShowDescription = { descriptionVisible = true },
                    onReact = { onReact(request.ownedPost) },
                    onReply = { onReply(request.ownedPost) },
                    onReshare = { onReshare(request.ownedPost) },
                    onShare = { sharePost(context, request.ownedPost.post) },
                    enabled = transition.phase == MediaViewerPhase.Open,
                    alpha = transition.chromeAlpha,
                )
            }
            if (descriptionVisible) {
                Surface(
                    Modifier.fillMaxSize().padding(top = 72.dp, bottom = 72.dp),
                    color = Color.Black.copy(alpha = .96f),
                ) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Description", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            IconButton(
                                onClick = { descriptionVisible = false },
                                modifier = Modifier.semantics { contentDescription = "Close description" },
                            ) { Icon(AppIcons.Close, null, tint = Color.White) }
                        }
                        Text(
                            attachments[pagerState.currentPage].description.orEmpty(),
                            Modifier.padding(top = 16.dp),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.transitionTransform(bounds: Rect, destination: Rect): Modifier = graphicsLayer {
    if (destination.width > 0f && destination.height > 0f) {
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = bounds.width / destination.width
        scaleY = bounds.height / destination.height
        translationX = bounds.left - destination.left * scaleX
        translationY = bounds.top - destination.top * scaleY
    }
}

private fun me.foxtails.palustris.domain.Attachment.imageWidth(): Float =
    (width ?: previewWidth ?: 4).toFloat().coerceAtLeast(1f)

private fun me.foxtails.palustris.domain.Attachment.imageHeight(): Float =
    (height ?: previewHeight ?: 3).toFloat().coerceAtLeast(1f)
