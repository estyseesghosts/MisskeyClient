@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.foxtails.palustris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.components.BeelineBubbleShape
import me.foxtails.palustris.ui.components.PillAction

private const val CompactHashtagLimit = 6

@Composable
internal fun HashtagBubble(
    hashtags: List<String>,
    maxHeight: Dp,
    openingKey: Long,
    onSelected: (String) -> Unit,
) {
    var expanded by remember(openingKey) { mutableStateOf(false) }
    val listDescription = stringResource(
        if (expanded) R.string.post_action_hashtags_expanded else R.string.post_action_hashtags,
    )
    val stateDescription = stringResource(
        if (expanded) R.string.post_action_bubble_expanded else R.string.post_action_bubble_collapsed,
    )
    Surface(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .testTag(if (expanded) "hashtag_bubble_expanded" else "hashtag_bubble_compact")
            .semantics {
                contentDescription = listDescription
                this.stateDescription = stateDescription
            },
        shape = BeelineBubbleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        if (expanded) {
            ExpandedHashtags(hashtags, onSelected) { expanded = false }
        } else {
            CompactHashtags(
                hashtags = hashtags,
                maxHeight = maxHeight,
                density = LocalDensity.current,
                onExpand = { expanded = true },
                onSelected = onSelected,
            )
        }
    }
}

@Composable
private fun CompactHashtags(
    hashtags: List<String>,
    maxHeight: Dp,
    density: androidx.compose.ui.unit.Density,
    onExpand: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelLarge
    BoxWithConstraints(Modifier.padding(8.dp)) {
        val visibleHashtags = remember(hashtags, maxWidth, maxHeight, density, textMeasurer, textStyle) {
            measuredCompactPrefix(
                hashtags = hashtags,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                density = density,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillAction(
                label = stringResource(R.string.post_action_hashtags_see_all),
                onClick = onExpand,
                modifier = Modifier.testTag("hashtag_bubble_expand"),
                contentDescription = stringResource(R.string.post_action_hashtags_see_all),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleHashtags.forEach { hashtag ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        HashtagPill(hashtag, onSelected)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedHashtags(
    hashtags: List<String>,
    onSelected: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(8.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(hashtags.chunked(2), key = { row -> row.joinToString("\u0000") }) { row ->
                Box(Modifier.fillMaxWidth()) {
                    FlowRow(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2,
                    ) {
                        row.forEach { hashtag -> HashtagPill(hashtag, onSelected) }
                    }
                }
            }
        }
        PillAction(
            label = stringResource(R.string.post_action_hashtags_close),
            onClick = onClose,
            modifier = Modifier.testTag("hashtag_bubble_close"),
            contentDescription = stringResource(R.string.post_action_hashtags_close),
        )
    }
}

@Composable
private fun HashtagPill(hashtag: String, onSelected: (String) -> Unit) {
    PillAction(
        label = hashtag,
        onClick = { onSelected(hashtag) },
        modifier = Modifier
            .widthIn(max = 240.dp)
            .testTag("hashtag_bubble_$hashtag"),
        contentDescription = stringResource(R.string.post_action_hashtag_description, hashtag),
        maxLines = 2,
    )
}

private fun measuredCompactPrefix(
    hashtags: List<String>,
    maxWidth: Dp,
    maxHeight: Dp,
    density: androidx.compose.ui.unit.Density,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
): List<String> {
    val width = maxWidth.coerceAtLeast(1.dp)
    val availableHeight = maxHeight - 72.dp
    if (availableHeight < 48.dp) return emptyList()

    var usedHeight = 0.dp
    val result = mutableListOf<String>()
    for (hashtag in hashtags.take(CompactHashtagLimit)) {
        val measured = textMeasurer.measure(
            AnnotatedString(hashtag),
            textStyle,
            constraints = Constraints(maxWidth = with(density) { minOf(width, 240.dp).roundToPx() }),
        )
        val pillHeight = maxOf(48.dp, with(density) { measured.size.height.toDp() } + 20.dp)
        val nextHeight = usedHeight + if (usedHeight == 0.dp) pillHeight else 8.dp + pillHeight
        if (nextHeight > availableHeight) break
        result += hashtag
        usedHeight = nextHeight
    }
    return result
}
