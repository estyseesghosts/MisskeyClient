package me.foxtails.palustris.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.ui.CompactSearchChipRowHeight

@Composable
internal fun CategoryChips(
    titles: List<String>,
    selected: Int?,
    rowContentDescription: String,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(CompactSearchChipRowHeight)
            .semantics { contentDescription = rowContentDescription },
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(titles.size) { index ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(titles[index]) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                modifier = Modifier
                    .height(CompactSearchChipRowHeight)
                    .semantics {
                        contentDescription = titles[index]
                        role = Role.Tab
                        this.selected = selected == index
                    },
                shape = RoundedCornerShape(50),
            )
        }
    }
}
