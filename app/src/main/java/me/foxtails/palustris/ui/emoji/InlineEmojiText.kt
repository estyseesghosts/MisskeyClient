package me.foxtails.palustris.ui.emoji

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.ui.openExternal

/** Cache scope for emoji image requests; account-scoped wrappers provide a real identity. */
internal val LocalEmojiAccountScope = staticCompositionLocalOf { "emoji-global" }

internal fun emojiScopeFor(accountId: AccountId): String =
    "${accountId.connection.origin}\u0000${accountId.localId}"

/**
 * One annotated rich-text renderer for emoji-aware text. Custom emoji render through
 * baseline-aligned inline content at `em` size; the shortcode stays visible while the
 * image loads or fails. Link interaction, selection/copy, ellipsis, and text styles are
 * preserved. The original shortcode text remains in the annotated string for copy and
 * accessibility.
 */
@Composable
fun InlineEmojiText(
    text: String,
    emoji: Map<String, CustomEmoji>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val linkStyle = SpanStyle(color = primary, textDecoration = TextDecoration.Underline)
    val model = remember(text, emoji) { EmojiTextParser.parse(text, emoji) }
    val inlineContent = buildInlineContent(emoji, style.fontSize)
    val annotated = remember(model) {
        buildAnnotatedString {
            model.segments.forEach { segment -> appendSegment(this, segment, linkStyle) }
        }
    }
    if (inlineContent.isEmpty()) {
        ClickableText(
            text = annotated,
            modifier = modifier,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
            onClick = { offset ->
                annotated.getStringAnnotations(URL_ANNOTATION, offset, offset)
                    .firstOrNull()?.let { openExternal(context, it.item) }
            },
        )
        return
    }
    var layoutResult: TextLayoutResult? by remember { mutableStateOf(null) }
    BasicText(
        text = annotated,
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { offset ->
                layoutResult?.let { layout ->
                    val position = layout.getOffsetForPosition(offset)
                    annotated.getStringAnnotations(URL_ANNOTATION, position, position)
                        .firstOrNull()?.let { openExternal(context, it.item) }
                }
            }
        },
        style = style,
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
    CompositionLocalProvider(LocalEmojiAccountScope provides emojiScopeFor(account.id)) {
        InlineEmojiText(account.displayName, account.emoji, modifier, style, maxLines, overflow)
    }
}

@Composable
fun PostText(
    post: Post,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    CompositionLocalProvider(LocalEmojiAccountScope provides emojiScopeFor(post.author.id)) {
        InlineEmojiText(post.text, post.emoji, modifier, style, maxLines, overflow)
    }
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
            val density = LocalDensity.current
            val scope = LocalEmojiAccountScope.current
            val imageRequest = remember(emoji, scope) {
                with(density) {
                    MediaImageLoader.get(context).emojiRequest(
                        context = context,
                        emoji = emoji,
                        request = request,
                        accountIdentity = scope,
                        emojiIdentity = emoji.submissionValue,
                        decodeSizePx = 96,
                    )
                }
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {},
            )
        }
    }
}

private const val URL_ANNOTATION = "URL"

private fun appendSegment(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    segment: RichTextSegment,
    linkStyle: SpanStyle,
) {
    when (segment) {
        is RichTextSegment.Text -> builder.append(segment.text)
        is RichTextSegment.Emoji -> builder.appendInlineContent(segment.token, alternateText = segment.token)
        is RichTextSegment.Link -> {
            builder.pushStringAnnotation(URL_ANNOTATION, segment.url)
            builder.pushStyle(linkStyle)
            segment.label.forEach { inner -> appendSegment(builder, inner, linkStyle) }
            builder.pop()
            builder.pop()
        }
    }
}

private fun buildInlineContent(
    emoji: Map<String, CustomEmoji>,
    emSize: TextUnit,
): Map<String, androidx.compose.foundation.text.InlineTextContent> {
    val ids = buildSet {
        emoji.forEach { (key, value) ->
            add(key)
            add(value.token)
            add(key.removeSurrounding(":"))
        }
    }
    return ids.associateWith { id ->
        val value = emoji[id] ?: emoji[id.removeSurrounding(":")] ?: emoji[id.trim(':')]
        androidx.compose.foundation.text.InlineTextContent(
            placeholder = androidx.compose.ui.text.Placeholder(
                width = emSize,
                height = emSize,
                placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            EmojiInlineContent(id, value, emSize)
        }
    }
}

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
