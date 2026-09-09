package me.foxtails.palustris.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.ui.CompactSearchChipRowHeight
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.motion.rememberSelectedScale
import me.foxtails.palustris.ui.motion.springPress

internal data class FilterChipEntry(
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val contentDescription: String = label,
    val role: Role = Role.Tab,
    val testTag: String? = null,
)

@Composable
internal fun FilterChipRow(
    entries: List<FilterChipEntry>,
    rowContentDescription: String,
) {
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(
            lazyListState = listState,
            snapPosition = androidx.compose.foundation.gestures.snapping.SnapPosition.Start,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(CompactSearchChipRowHeight)
            .semantics { contentDescription = rowContentDescription },
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { "${it.role}:${it.label}" }) { entry ->
            val interactionSource = remember(entry.label) { MutableInteractionSource() }
            val selectedContainerColor = rememberSelectedColor(
                entry.selected,
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.surfaceContainer,
            )
            val selectedContentColor = rememberSelectedColor(
                entry.selected,
                MaterialTheme.colorScheme.onSecondaryContainer,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val selectedScale = rememberSelectedScale(entry.selected)
            FilterChip(
                selected = entry.selected,
                onClick = entry.onClick,
                enabled = entry.enabled,
                interactionSource = interactionSource,
                label = { Text(entry.label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = selectedContainerColor,
                    labelColor = selectedContentColor,
                    selectedContainerColor = selectedContainerColor,
                    selectedLabelColor = selectedContentColor,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                modifier = Modifier
                    .height(CompactSearchChipRowHeight)
                    .springPress(interactionSource, pressedScale = LocalPalustrisMotionScheme.current.pressedScale)
                    .graphicsLayer {
                        scaleX = selectedScale
                        scaleY = selectedScale
                    }
                    .then(entry.testTag?.let { Modifier.testTag(it) } ?: Modifier)
                    .semantics {
                        contentDescription = entry.contentDescription
                        role = entry.role
                        this.selected = entry.selected
                    },
                shape = RoundedCornerShape(50),
            )
        }
    }
}

@Composable
internal fun CategoryChips(
    titles: List<String>,
    selected: Int?,
    rowContentDescription: String,
    onSelect: (Int) -> Unit,
) {
    FilterChipRow(
        entries = titles.mapIndexed { index, title ->
            FilterChipEntry(
                label = title,
                selected = selected == index,
                onClick = { onSelect(index) },
            )
        },
        rowContentDescription = rowContentDescription,
    )
}
