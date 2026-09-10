@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.MediaRequestDecision
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.MediaRequestRole
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.openExternal
import me.foxtails.palustris.ui.sharePost
import kotlin.math.max
import kotlin.math.roundToInt

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
    val fullReadyPages = remember(request.transitionKey) { mutableStateMapOf<Int, Boolean>() }
    val fullDimensions = remember(request.transitionKey) { mutableStateMapOf<Int, Size>() }
    var menuVisible by rememberSaveable { mutableStateOf(false) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var descriptionVisible by rememberSaveable { mutableStateOf(false) }
    var closeRequested by remember(request.transitionKey) { mutableStateOf(false) }
    var transitionOwner by remember(request.transitionKey) { mutableStateOf<MediaTransitionOwner?>(null) }
    val scope = rememberCoroutineScope()
    val zoomStates = remember(request.transitionKey) { mutableStateMapOf<Int, ZoomableMediaState>() }
    val selectedZoomScale = zoomStates[pagerState.currentPage]?.scale ?: 1f
    val selectedAttachment = attachments[pagerState.currentPage]
    val selectedTransitionKey = request.sourceKeys.getOrNull(pagerState.currentPage)
        ?: if (pagerState.currentPage == request.attachmentIndex) {
            request.transitionKey
        } else {
            MediaTransitionKey.forAttachment(request.ownedPost, pagerState.currentPage)
        }
    val selectedPage = pagerState.currentPage
    val selectedKey = selectedTransitionKey
    val selectedFullReady = fullReadyPages[selectedPage] == true
    val latestDescriptionVisible by rememberUpdatedState(descriptionVisible)
    val latestZoomScale by rememberUpdatedState(selectedZoomScale)

    DisposableEffect(request.transitionKey) {
        onDispose { transitionOwner?.let { registry.end(it) } }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Media viewer" },
    ) {
        val viewport = with(density) { Rect(0f, 0f, maxWidth.toPx(), maxHeight.toPx()) }
        val selectedSource = registry.sourceFor(selectedTransitionKey)
            ?: request.initialSource.takeIf { selectedTransitionKey == request.transitionKey }
        val initialAttachment = attachments[request.attachmentIndex.coerceIn(0, attachments.lastIndex)]
        val initialDestinationFrame = destinationFrame(
            viewport,
            initialAttachment,
            density,
            fullDimensions[request.attachmentIndex.coerceIn(0, attachments.lastIndex)],
        )
        val transition = remember(request.transitionKey) {
            MediaViewerTransitionState(
                initialSourceBounds = request.initialSourceBounds,
                destinationBounds = initialDestinationFrame.clipBounds,
                motionScheme = motionScheme,
                initialSourceFrame = sourceFrame(request.initialSource, request.initialSourceBounds, initialAttachment),
                initialDestinationFrame = initialDestinationFrame,
            )
        }
        val selectedDestinationFrame = destinationFrame(viewport, selectedAttachment, density, fullDimensions[selectedPage])
        val selectedSourceFrame = sourceFrame(selectedSource, Rect.Zero, selectedAttachment)
        val transitionLayerVisible = transition.phase != MediaViewerPhase.Open || !selectedFullReady
        val mediaLoader = remember(context) { MediaImageLoader.get(context) }
        val fullRequest = remember(
            selectedAttachment,
            selectedTransitionKey,
            request.ownedPost.fetchedBy,
            request.ownedPost.post.id,
            viewport,
        ) {
            mediaRequest(
                context = context,
                mediaLoader = mediaLoader,
                attachment = selectedAttachment,
                role = MediaRequestRole.Full,
                accountIdentity = request.ownedPost.fetchedBy.toString(),
                postIdentity = "${request.ownedPost.post.id.connection}/${request.ownedPost.post.id.value}",
                index = pagerState.currentPage,
                widthPx = viewport.width.roundToInt(),
                heightPx = viewport.height.roundToInt(),
            )
        }
        val previewRequest = selectedSource?.previewRequest ?: remember(
            selectedAttachment,
            selectedTransitionKey,
            request.ownedPost.fetchedBy,
            request.ownedPost.post.id,
            viewport,
        ) {
            mediaRequest(
                context = context,
                mediaLoader = mediaLoader,
                attachment = selectedAttachment,
                role = MediaRequestRole.Preview,
                accountIdentity = request.ownedPost.fetchedBy.toString(),
                postIdentity = "${request.ownedPost.post.id.connection}/${request.ownedPost.post.id.value}",
                index = pagerState.currentPage,
                widthPx = viewport.width.roundToInt(),
                heightPx = viewport.height.roundToInt(),
            )
        }

        transition.updateViewport(viewport)
        LaunchedEffect(selectedTransitionKey) {
            transitionOwner = registry.begin(selectedTransitionKey)
            if (selectedTransitionKey == request.transitionKey && transition.phase == MediaViewerPhase.Opening) {
                transition.updateSourceFrame(selectedSourceFrame)
                transition.updateDestinationFrame(selectedDestinationFrame)
                transition.startOpening()
            } else if (selectedTransitionKey != request.transitionKey) {
                transition.selectPage(selectedSourceFrame, selectedDestinationFrame)
            }
        }
        LaunchedEffect(transition.phase, selectedTransitionKey, selectedDestinationFrame) {
            if (transition.phase == MediaViewerPhase.Open) {
                transition.updateDestinationFrame(selectedDestinationFrame)
            }
        }

        fun currentTargetFrame(): MediaTransitionFrame? = registry.sourceFor(selectedTransitionKey)?.let {
            sourceFrame(it, Rect.Zero, selectedAttachment)
        }

        fun requestClose(releaseVelocity: Offset = Offset.Zero) {
            if (transition.isClosing || closeRequested) return
            closeRequested = true
            val target = currentTargetFrame()
            val owner = transitionOwner
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                transition.close(
                    targetBounds = target?.clipBounds,
                    viewport = viewport,
                    releaseVelocity = releaseVelocity,
                    targetFrame = target,
                )
                owner?.takeIf(registry::isOwnerActive)?.let {
                    registry.prepareHandoff(it)
                    withFrameNanos { }
                }
                onClose()
            }
        }

        BackHandler {
            if (descriptionVisible) descriptionVisible = false else requestClose()
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = transition.backgroundAlpha))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                        var ownsGesture = false
                        try {
                            if (latestDescriptionVisible || latestZoomScale > 1.01f || transition.isClosing || transition.phase != MediaViewerPhase.Open) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    continue
                            }
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var previous = down.position
                            while (!ownsGesture) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.singleOrNull() ?: break
                                if (!change.pressed) break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val total = change.position - down.position
                                if (total.getDistance() >= viewConfiguration.touchSlop) {
                                    if (kotlin.math.abs(total.x) > kotlin.math.abs(total.y) * 1.15f) {
                                        break
                                    }
                                    ownsGesture = true
                                    transition.beginDrag()
                                    transition.dragBy(total)
                                    change.consume()
                                }
                                previous = change.position
                            }
                            while (ownsGesture) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.singleOrNull()
                                if (change == null) {
                                    ownsGesture = false
                                    scope.launch(start = CoroutineStart.UNDISPATCHED) { transition.returnToOpen() }
                                    break
                                }
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    ownsGesture = false
                                    val measuredVelocity = tracker.calculateVelocity()
                                    val velocity = Offset(
                                        measuredVelocity.x.safeGestureVelocity(),
                                        measuredVelocity.y.safeGestureVelocity(),
                                    )
                                    if (transition.shouldDismiss(velocity, with(density) { 1_400.dp.toPx() })) {
                                        requestClose(velocity)
                                    } else {
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            transition.returnToOpen()
                                        }
                                    }
                                    break
                                }
                                transition.dragBy(change.position - previous)
                                previous = change.position
                                change.consume()
                            }
                        } finally {
                            if (ownsGesture && transition.phase == MediaViewerPhase.Dragging) {
                                ownsGesture = false
                                scope.launch(start = CoroutineStart.UNDISPATCHED) { transition.returnToOpen() }
                            }
                        }
                        }
                    }
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = selectedZoomScale <= 1.01f && !transition.isDismissGestureActive && !transition.isClosing,
            ) { page ->
                val attachment = attachments[page]
                val zoomState = zoomStates.getOrPut(page) { ZoomableMediaState() }
                val selected = page == pagerState.currentPage
                val pageModifier = if (selected && transitionLayerVisible) {
                    Modifier.graphicsLayer { alpha = 0f }
                } else {
                    Modifier
                }
                MediaPage(
                    attachment = attachment,
                    index = page,
                    selected = selected,
                    revealed = !attachment.sensitive || revealedPages[page] == true,
                    accountIdentity = request.ownedPost.fetchedBy.toString(),
                    postIdentity = "${request.ownedPost.post.id.connection}/${request.ownedPost.post.id.value}",
                    onReveal = { revealedPages[page] = true },
                    onImageReady = {
                        if (selected) fullReadyPages[page] = true
                    },
                    onImageDimensionsReady = { size ->
                        if (selected && pagerState.currentPage == page && selectedTransitionKey == selectedKey) {
                            fullDimensions[page] = size
                        }
                    },
                    zoomState = zoomState,
                    modifier = pageModifier,
                )
                zoomStates[page] = zoomState
            }

            if (transitionLayerVisible) {
                MediaTransitionImage(
                    frame = transition.visualFrame,
                    previewRequest = previewRequest,
                    fullRequest = fullRequest,
                    imageLoader = mediaLoader.imageLoader,
                    useFullImage = transition.phase == MediaViewerPhase.Open && selectedFullReady,
                    onFullImageReady = {
                        if (pagerState.currentPage == selectedPage && selectedTransitionKey == selectedKey) {
                            fullReadyPages[selectedPage] = true
                        }
                    },
                )
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

@Composable
private fun MediaTransitionImage(
    frame: MediaTransitionFrame,
    previewRequest: ImageRequest?,
    fullRequest: ImageRequest?,
    imageLoader: coil.ImageLoader,
    useFullImage: Boolean,
    onFullImageReady: () -> Unit,
) {
    if (!frame.visibleBounds.isValid() || (previewRequest == null && fullRequest == null)) return
    val previewPainter = previewRequest?.let { rememberAsyncImagePainter(it, imageLoader) }
    val fullPainter = fullRequest?.let { rememberAsyncImagePainter(it, imageLoader) }
    val fullReady = fullPainter?.state is AsyncImagePainter.State.Success
    LaunchedEffect(fullPainter?.state) {
        if (fullPainter?.state is AsyncImagePainter.State.Success) onFullImageReady()
    }
    val painter = if (useFullImage && fullReady) fullPainter else previewPainter ?: fullPainter
    if (painter == null) return
    MediaTransitionImageCanvas(painter, frame)
}

@Composable
internal fun MediaTransitionImageCanvas(
    painter: Painter,
    frame: MediaTransitionFrame,
) {
    if (!frame.visibleBounds.isValid() || !frame.clipBounds.isValid() || !frame.imageBounds.isValid()) return
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val radius = frame.cornerRadiusPx.coerceIn(
                    0f,
                    minOf(frame.clipBounds.width, frame.clipBounds.height) / 2f,
                )
                val roundedClip = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = frame.clipBounds,
                            radiusX = radius,
                            radiusY = radius,
                        ),
                    )
                }
                onDrawWithContent {
                    drawContent()
                    clipRect(
                        left = frame.visibleBounds.left,
                        top = frame.visibleBounds.top,
                        right = frame.visibleBounds.right,
                        bottom = frame.visibleBounds.bottom,
                    ) {
                        clipPath(roundedClip) {
                            translate(frame.imageBounds.left, frame.imageBounds.top) {
                                with(painter) {
                                    draw(
                                        size = Size(frame.imageBounds.width, frame.imageBounds.height),
                                    )
                                }
                            }
                        }
                    }
                }
            },
    )
}

private fun mediaRequest(
    context: android.content.Context,
    mediaLoader: MediaImageLoader,
    attachment: me.foxtails.palustris.domain.Attachment,
    role: MediaRequestRole,
    accountIdentity: String,
    postIdentity: String,
    index: Int,
    widthPx: Int,
    heightPx: Int,
): ImageRequest? {
    val decision = MediaRequestPolicy.resolve(attachment, role, revealed = true, explicitlyOpened = role == MediaRequestRole.Full)
    return (decision as? MediaRequestDecision.Request)?.let {
        mediaLoader.request(
            context = context,
            decision = it,
            accountIdentity = accountIdentity,
            postIdentity = postIdentity,
            attachment = attachment,
            attachmentIndex = index,
            decodeWidthPx = widthPx,
            decodeHeightPx = heightPx,
        )
    }
}

private fun sourceFrame(
    source: MediaTransitionSource?,
    fallbackBounds: Rect,
    attachment: me.foxtails.palustris.domain.Attachment,
): MediaTransitionFrame {
    val fullBounds = source?.fullBounds ?: fallbackBounds
    if (!fullBounds.isValid()) return MediaTransitionFrame(Rect.Zero, Rect.Zero, Rect.Zero)
    val imageWidth = source?.imageWidth ?: attachment.imageWidth()
    val imageHeight = source?.imageHeight ?: attachment.imageHeight()
    return MediaTransitionFrame(
        imageBounds = cropRect(fullBounds, imageWidth, imageHeight),
        clipBounds = fullBounds,
        visibleBounds = source?.visibleBounds?.takeIf { it.isValid() } ?: fullBounds,
        cornerRadiusPx = source?.cornerRadiusPx ?: 0f,
    )
}

private fun destinationFrame(
    viewport: Rect,
    attachment: me.foxtails.palustris.domain.Attachment,
    density: androidx.compose.ui.unit.Density,
    drawableSize: Size? = null,
): MediaTransitionFrame {
    val contentBounds = mediaPageContentBounds(
        viewport,
        with(density) { MediaPageHorizontalPadding.toPx() },
    )
    val imageBounds = fitRect(
        contentBounds,
        drawableSize?.width ?: attachment.imageWidth(),
        drawableSize?.height ?: attachment.imageHeight(),
    )
    return MediaTransitionFrame(imageBounds, imageBounds, imageBounds)
}

internal fun cropRect(container: Rect, imageWidth: Float, imageHeight: Float): Rect {
    if (!container.isValid() || imageWidth <= 0f || imageHeight <= 0f) return container
    val scale = max(container.width / imageWidth, container.height / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return Rect(
        left = container.center.x - width / 2f,
        top = container.center.y - height / 2f,
        right = container.center.x + width / 2f,
        bottom = container.center.y + height / 2f,
    )
}

private fun me.foxtails.palustris.domain.Attachment.imageWidth(): Float =
    (width ?: previewWidth ?: 4).toFloat().coerceAtLeast(1f)

private fun me.foxtails.palustris.domain.Attachment.imageHeight(): Float =
    (height ?: previewHeight ?: 3).toFloat().coerceAtLeast(1f)

private fun Rect.isValid(): Boolean = width > 0f && height > 0f

private fun Float.safeGestureVelocity(): Float = takeIf { isFinite() }?.coerceIn(-10_000f, 10_000f) ?: 0f
