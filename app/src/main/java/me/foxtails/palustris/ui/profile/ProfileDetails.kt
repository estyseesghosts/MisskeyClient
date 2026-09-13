package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.openExternal
import me.foxtails.palustris.domain.Account

@Composable
internal fun ProfileDetails(account: Account) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("profile_details"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.profile_details), style = MaterialTheme.typography.titleLarge)
        if (account.profileFields.isEmpty()) {
            Text(
                stringResource(R.string.profile_no_details),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            account.profileFields.forEachIndexed { index, field ->
                val context = LocalContext.current
                val clickable = field.value.isWebAddress()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (clickable) Modifier.clickable { openExternal(context, field.value) } else Modifier)
                        .padding(vertical = 4.dp)
                        .testTag("profile_field_$index"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InlineEmojiText(
                        text = field.name,
                        emoji = account.emoji,
                        modifier = Modifier.width(112.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    InlineEmojiText(
                        text = field.value,
                        emoji = account.emoji,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (clickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
                if (index < account.profileFields.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
            }
        }
    }
}

private fun String.isWebAddress(): Boolean = startsWith("https://") || startsWith("http://")
