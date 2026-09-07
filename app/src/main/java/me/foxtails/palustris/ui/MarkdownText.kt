package me.foxtails.palustris.ui

import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.MaterialTheme

private val markdownLinkPattern = Regex("\\[([^]\\r\\n]*)\\]\\((https?://[^)\\s]+)\\)")

@Composable
internal fun MarkdownPostText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        var cursor = 0
        markdownLinkPattern.findAll(text).forEach { match ->
            append(text, cursor, match.range.first)
            pushStringAnnotation("URL", match.groupValues[2])
            withStyle(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)) {
                append(match.groupValues[1])
            }
            pop()
            cursor = match.range.last + 1
        }
        append(text, cursor, text.length)
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { openExternal(context, it.item) }
        },
    )
}
