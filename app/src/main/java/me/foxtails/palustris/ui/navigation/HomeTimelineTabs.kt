package me.foxtails.palustris.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.ui.components.BeelineBubbleShape
import me.foxtails.palustris.ui.timelineDescriptionRes
import me.foxtails.palustris.ui.timelineLabelRes

@Composable
internal fun HomeTimelineTabs(
    timelines: Set<Timeline>,
    selected: Timeline,
    onSelect: (Timeline) -> Unit,
    modifier: Modifier = Modifier,
) {
    val orderedTimelines = remember(timelines) {
        timelineDisplayOrder.filter { it in timelines }
    }
    val listState = rememberLazyListState()
    val selectedIndex = orderedTimelines.indexOf(selected)
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { selectableGroup() }
            .testTag("home_timeline_tabs"),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(orderedTimelines, key = { it.name }) { timeline ->
            val label = stringResource(timelineLabelRes(timeline))
            val description = stringResource(R.string.large_timeline, label)
            val timelineDescription = stringResource(timelineDescriptionRes(timeline))
            val isSelected = timeline == selected
            val interactionSource = remember(timeline) { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .bubblePressLayer(
                        pressed = pressed,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        shape = BeelineBubbleShape,
                    )
                    .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                        onSelect(timeline)
                    }
                    .testTag("home_timeline_tab_${timeline.name}")
                    .semantics {
                        contentDescription = description
                        stateDescription = timelineDescription
                        this.selected = isSelected
                        role = Role.Tab
                    },
            ) {
                Surface(
                    shape = BeelineBubbleShape,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            label,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
