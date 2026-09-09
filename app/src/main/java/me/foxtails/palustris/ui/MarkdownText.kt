package me.foxtails.palustris.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import me.foxtails.palustris.ui.emoji.InlineEmojiText

/**
 * Markdown-capable post text. The shared emoji-aware renderer handles links and text
 * styles; no second emoji parser runs over its output.
 */
@Composable
internal fun MarkdownPostText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    InlineEmojiText(
        text = text,
        emoji = emptyMap(),
        modifier = modifier,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
    )
}
