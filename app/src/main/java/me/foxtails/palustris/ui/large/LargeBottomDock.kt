package me.foxtails.palustris.ui.large

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.timelineLabelRes

internal val LargeBottomDockClearance = 88.dp
internal val LargeSearchDockClearance = 144.dp

@Composable
internal fun LargeBottomDock(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
internal fun LargeTimelineDockContent(
    timelines: Set<Timeline>,
    selected: Timeline,
    onSelect: (Timeline) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Timeline.entries.filter { it in timelines }.forEach { timeline ->
            val label = stringResource(timelineLabelRes(timeline))
            FilterChip(
                selected = timeline == selected,
                onClick = { onSelect(timeline) },
                 label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                shape = RoundedCornerShape(50),
                 modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}
