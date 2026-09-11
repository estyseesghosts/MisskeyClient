package me.foxtails.palustris.ui.emoji

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import coil.compose.AsyncImage
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.openExternal

/**
 * One annotated rich-text renderer for emoji-aware text. Post callers can opt into inline
 * entity bubbles; other text surfaces keep their existing plain/Markdown presentation.
 */
@Composable
fun InlineEmojiText(
    text: String,
    emoji: Map<String, CustomEmoji>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    enableInlineEntities: Boolean = false,
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    onSearchHashtag: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val resolvedStyle = style.copy(
        color = if (style.color == Color.Unspecified) LocalContentColor.current else style.color,
    )
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    val model = remember(text, emoji) { EmojiTextParser.parse(text, emoji) }
    val bubbleTextStyle = MaterialTheme.typography.labelMedium
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val inlineContent = remember(model, style.fontSize, enableInlineEntities, onOpenUrl, onOpenUsername, onSearchHashtag) {
        buildInlineContent(
            model = model,
            emSize = style.fontSize,
            enableInlineEntities = enableInlineEntities,
            textMeasurer = textMeasurer,
            density = density,
            bubbleTextStyle = bubbleTextStyle,
            onOpenUrl = { url -> onOpenUrl?.invoke(url) ?: openExternal(context, url) },
            onOpenUsername = onOpenUsername,
            onSearchHashtag = onSearchHashtag,
        )
    }
    val annotated = remember(model, linkStyle, enableInlineEntities) {
        androidx.compose.ui.text.buildAnnotatedString {
            model.segments.forEach { segment ->
                appendSegment(this, model.source, segment, linkStyle, enableInlineEntities)
            }
        }
    }
    val hasInteractiveEntities = remember(annotated, enableInlineEntities) {
        annotated.getStringAnnotations(URL_ANNOTATION, 0, annotated.length).isNotEmpty() ||
            (enableInlineEntities && annotated.getStringAnnotations(USERNAME_ANNOTATION, 0, annotated.length).isNotEmpty()) ||
            (enableInlineEntities && annotated.getStringAnnotations(HASHTAG_ANNOTATION, 0, annotated.length).isNotEmpty())
    }
    if (!hasInteractiveEntities) {
        BasicText(
            text = annotated,
            modifier = modifier,
            style = resolvedStyle,
            maxLines = maxLines,
            overflow = overflow,
            inlineContent = inlineContent,
        )
        return
    }

    var layoutResult: TextLayoutResult? by remember { mutableStateOf(null) }
    BasicText(
        text = annotated,
        modifier = modifier.pointerInput(annotated, enableInlineEntities) {
            detectTapGestures { offset ->
                val position = layoutResult?.getOffsetForPosition(offset) ?: return@detectTapGestures
                annotated.getStringAnnotations(URL_ANNOTATION, position, position)
                    .firstOrNull()?.let { annotation ->
                        onOpenUrl?.invoke(annotation.item) ?: openExternal(context, annotation.item)
                        return@detectTapGestures
                    }
                if (enableInlineEntities) {
                    annotated.getStringAnnotations(USERNAME_ANNOTATION, position, position)
                        .firstOrNull()?.let { onOpenUsername?.invoke(it.item) }
                    annotated.getStringAnnotations(HASHTAG_ANNOTATION, position, position)
                        .firstOrNull()?.let { onSearchHashtag?.invoke(it.item) }
                }
            }
        },
        style = resolvedStyle,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inlineContent,
        onTextLayout = { layoutResult = it },
    )
}

@Composable
fun AccountDisplayName(
    account: Account,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    InlineEmojiText(account.displayName, account.emoji, modifier, style, maxLines, overflow)
}

@Composable
fun PostText(
    post: Post,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    onSearchHashtag: ((String) -> Unit)? = null,
) {
    InlineEmojiText(
        text = post.text,
        emoji = post.emoji,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        enableInlineEntities = true,
        onOpenUrl = onOpenUrl,
        onOpenUsername = onOpenUsername,
        onSearchHashtag = onSearchHashtag,
    )
}

/**
 * Shared custom-emoji image cell for chips and notification surfaces. Renders the
 * fallback text until the image loads and whenever the request fails.
 */
@Composable
fun CustomEmojiImage(
    emoji: CustomEmoji?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    val request = emoji?.let(MediaRequestPolicy::emojiImage)
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(
            fallbackText,
            style = textStyle,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        if (emoji != null && request != null) {
            val context = LocalContext.current
            val mediaImageLoader = remember(context) { MediaImageLoader.get(context) }
            val imageRequest = remember(emoji, request, mediaImageLoader) {
                mediaImageLoader.emojiRequest(
                    context = context,
                    request = request,
                    decodeSizePx = 96,
                )
            }
            AsyncImage(
                model = imageRequest,
                imageLoader = mediaImageLoader.emojiImageLoader,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
            )
        }
    }
}

private const val URL_ANNOTATION = "URL"
private const val USERNAME_ANNOTATION = "USERNAME"
private const val HASHTAG_ANNOTATION = "HASHTAG"

private fun appendSegment(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    source: String,
    segment: RichTextSegment,
    linkStyle: SpanStyle,
    enableInlineEntities: Boolean,
) {
    when (segment) {
        is RichTextSegment.Text -> builder.append(segment.text)
        is RichTextSegment.Emoji -> builder.appendInlineContent(segment.token, alternateText = segment.token)
        is RichTextSegment.Link -> {
            if (enableInlineEntities) {
                builder.pushStringAnnotation(URL_ANNOTATION, segment.target)
                builder.appendInlineContent(inlineContentId(segment), alternateText = segment.displayLabel)
                builder.pop()
            } else if (segment.plainUrl) {
                builder.append(source.substring(segment.range))
            } else {
                builder.pushStringAnnotation(URL_ANNOTATION, segment.target)
                builder.pushStyle(linkStyle)
                segment.label.forEach { inner -> appendSegment(builder, source, inner, linkStyle, false) }
                builder.pop()
                builder.pop()
            }
        }
        is RichTextSegment.Username -> {
            if (enableInlineEntities) {
                builder.pushStringAnnotation(USERNAME_ANNOTATION, segment.target)
                builder.appendInlineContent(inlineContentId(segment), alternateText = segment.displayLabel)
                builder.pop()
            } else {
                builder.append(source.substring(segment.range))
            }
        }
        is RichTextSegment.Hashtag -> {
            if (enableInlineEntities) {
                builder.pushStringAnnotation(HASHTAG_ANNOTATION, segment.target)
                builder.appendInlineContent(inlineContentId(segment), alternateText = segment.displayLabel)
                builder.pop()
            } else {
                builder.append(source.substring(segment.range))
            }
        }
    }
}

private fun buildInlineContent(
    model: RichTextModel,
    emSize: TextUnit,
    enableInlineEntities: Boolean,
    textMeasurer: TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    bubbleTextStyle: TextStyle,
    onOpenUrl: (String) -> Unit,
    onOpenUsername: ((String) -> Unit)?,
    onSearchHashtag: ((String) -> Unit)?,
): Map<String, androidx.compose.foundation.text.InlineTextContent> = buildMap {
    fun addSegments(segments: List<RichTextSegment>) {
        segments.forEach { segment ->
            when (segment) {
                is RichTextSegment.Emoji -> put(
                    segment.token,
                    androidx.compose.foundation.text.InlineTextContent(
                        placeholder = androidx.compose.ui.text.Placeholder(
                            width = emSize,
                            height = emSize,
                            placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) { EmojiInlineContent(segment.token, segment.emoji, emSize) },
                )
                is RichTextSegment.Link -> {
                    if (enableInlineEntities) {
                        put(
                            inlineContentId(segment),
                            entityContent(
                                label = segment.displayLabel,
                                description = "Link ${segment.displayLabel}",
                                emSize = emSize,
                                width = entityWidthEm(segment.displayLabel, true, emSize, textMeasurer, density, bubbleTextStyle),
                                onClick = { onOpenUrl(segment.target) },
                                isLink = true,
                            ),
                        )
                    } else {
                        addSegments(segment.label)
                    }
                }
                is RichTextSegment.Username -> if (enableInlineEntities) {
                    put(
                        inlineContentId(segment),
                        entityContent(
                            label = segment.displayLabel,
                            description = "Username ${segment.displayLabel}",
                            emSize = emSize,
                            width = entityWidthEm(segment.displayLabel, false, emSize, textMeasurer, density, bubbleTextStyle),
                            onClick = onOpenUsername?.let { callback -> { callback(segment.target) } },
                            isLink = false,
                        ),
                    )
                }
                is RichTextSegment.Hashtag -> if (enableInlineEntities) {
                    put(
                        inlineContentId(segment),
                        entityContent(
                            label = segment.displayLabel,
                            description = "Hashtag ${segment.displayLabel}",
                            emSize = emSize,
                            width = entityWidthEm(segment.displayLabel, false, emSize, textMeasurer, density, bubbleTextStyle),
                            onClick = onSearchHashtag?.let { callback -> { callback(segment.target) } },
                            isLink = false,
                        ),
                    )
                }
                is RichTextSegment.Text -> Unit
            }
        }
    }
    addSegments(model.segments)
}

private fun entityContent(
    label: String,
    description: String,
    emSize: TextUnit,
    width: TextUnit,
    onClick: (() -> Unit)?,
    isLink: Boolean,
): androidx.compose.foundation.text.InlineTextContent = androidx.compose.foundation.text.InlineTextContent(
    placeholder = androidx.compose.ui.text.Placeholder(
        width = width,
        height = 1.5.em,
        placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
    ),
) {
    val iconSize = with(LocalDensity.current) {
        if (emSize == TextUnit.Unspecified) 14.dp else emSize.toDp() * .78f
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .then(
                onClick?.let { callback ->
                    Modifier.clickable(role = Role.Button, onClick = callback)
                } ?: Modifier,
            )
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(Modifier.fillMaxSize()) {
            androidx.compose.material3.Text(
                text = label,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            if (isLink) {
                Icon(
                    AppIcons.Paperclip,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(iconSize),
                )
            }
        }
    }
}

private fun entityWidthEm(
    label: String,
    isLink: Boolean,
    emSize: TextUnit,
    textMeasurer: TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    bubbleTextStyle: TextStyle,
): TextUnit {
    val labelWidthPx = textMeasurer.measure(
        text = AnnotatedString(label),
        style = bubbleTextStyle,
        maxLines = 1,
    ).size.width.toFloat()
    val parentFontSizePx = with(density) {
        (if (emSize == TextUnit.Unspecified) 16.sp else emSize).toPx()
    }
    val horizontalPaddingPx = with(density) { 12.dp.toPx() }
    val iconWidthPx = if (isLink) {
        with(density) { (if (emSize == TextUnit.Unspecified) 14.dp else emSize.toDp() * .78f).toPx() } +
            with(density) { 3.dp.toPx() }
    } else {
        0f
    }
    return ((labelWidthPx + horizontalPaddingPx + iconWidthPx) / parentFontSizePx).em
}

private fun inlineContentId(segment: RichTextSegment): String =
    "entity-${segment.range.first}-${segment.range.last}"

@Composable
private fun EmojiInlineContent(token: String, emoji: CustomEmoji?, emSize: TextUnit) {
    val density = LocalDensity.current
    val size = with(density) { emSize.toDp() }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        CustomEmojiImage(
            emoji = emoji,
            fallbackText = token,
            modifier = Modifier.size(size),
            textStyle = TextStyle(fontSize = emSize),
        )
    }
}
