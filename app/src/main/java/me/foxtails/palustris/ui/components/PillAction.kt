package me.foxtails.palustris.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A compact action control shared by anchored post controls and action sheets. */
@Composable
internal fun PillAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = label,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1,
    loading: Boolean = false,
    fillContent: Boolean = false,
) {
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier
                .then(if (fillContent) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                modifier = Modifier.then(if (fillContent) Modifier.fillMaxWidth() else Modifier),
                style = MaterialTheme.typography.labelLarge,
                textAlign = textAlign,
                maxLines = maxLines,
                overflow = if (maxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip,
            )
        }
    }
}
