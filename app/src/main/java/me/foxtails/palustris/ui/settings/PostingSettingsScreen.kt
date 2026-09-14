package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import androidx.compose.ui.Modifier
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.PostPreferences

@Composable
fun PostingSettingsScreen(
    preferences: PostPreferences,
    onDefaultAudience: (Audience) -> Unit,
    onRepliesUnlisted: (Boolean) -> Unit,
) {
    val options = Audience.entries.filter { it != Audience.Direct }
    Column(Modifier.fillMaxWidth()) {
        options.forEach { audience ->
            ListItem(
                headlineContent = { Text(audience.label()) },
                supportingContent = { Text(stringResource(R.string.settings_posting_audience_summary)) },
                trailingContent = {
                    androidx.compose.material3.RadioButton(
                        selected = preferences.defaultAudience == audience,
                        onClick = { onDefaultAudience(audience) },
                    )
                },
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_posting_replies_unlisted)) },
            supportingContent = { Text(stringResource(R.string.settings_posting_replies_unlisted_summary)) },
            trailingContent = {
                Switch(
                    checked = preferences.repliesUnlisted,
                    onCheckedChange = onRepliesUnlisted,
                )
            },
        )
    }
}

private fun Audience.label(): String = when (this) {
    Audience.Public -> "Everyone"
    Audience.Unlisted -> "Unlisted"
    Audience.Followers -> "Followers only"
    Audience.Direct -> "Direct"
}
