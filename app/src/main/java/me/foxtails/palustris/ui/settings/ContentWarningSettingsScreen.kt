package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ListItem
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.HiddenContentPresentation

@Composable
fun ContentWarningSettingsScreen(
    rules: ContentWarningRules,
    onChanged: (ContentWarningRules) -> Unit,
    localRules: ContentWarningRules = ContentWarningRules(),
    onLocalChanged: (ContentWarningRules) -> Unit = {},
    hiddenPresentation: HiddenContentPresentation = HiddenContentPresentation.Placeholder,
    onHiddenPresentation: (HiddenContentPresentation) -> Unit = {},
    localAccountLabel: String? = null,
) {
    var hideKeywords by remember(rules) { mutableStateOf(rules.hideKeywords.joinToString(", ")) }
    var hideHashtags by remember(rules) { mutableStateOf(rules.hideHashtags.joinToString(", ")) }
    var expandKeywords by remember(rules) { mutableStateOf(rules.expandKeywords.joinToString(", ")) }
    var expandHashtags by remember(rules) { mutableStateOf(rules.expandHashtags.joinToString(", ")) }
    var localHideKeywords by remember(localRules) { mutableStateOf(localRules.hideKeywords.joinToString(", ")) }
    var localHideHashtags by remember(localRules) { mutableStateOf(localRules.hideHashtags.joinToString(", ")) }
    var localExpandKeywords by remember(localRules) { mutableStateOf(localRules.expandKeywords.joinToString(", ")) }
    var localExpandHashtags by remember(localRules) { mutableStateOf(localRules.expandHashtags.joinToString(", ")) }
    Column(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_content_warning_hide_all)) },
            supportingContent = { Text(stringResource(R.string.settings_content_warning_hide_all_summary)) },
            trailingContent = { Switch(rules.hideAll, { onChanged(rules.copy(hideAll = it)) }) },
        )
        Text(stringResource(R.string.settings_content_warning_presentation), Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        HiddenContentPresentation.entries.forEach { presentation ->
            ListItem(
                headlineContent = { Text(if (presentation == HiddenContentPresentation.Remove) stringResource(R.string.settings_content_warning_remove_entries) else stringResource(R.string.settings_content_warning_placeholder_entries)) },
                supportingContent = { Text(if (presentation == HiddenContentPresentation.Remove) stringResource(R.string.settings_content_warning_remove_detail) else stringResource(R.string.settings_content_warning_placeholder_detail)) },
                leadingContent = { androidx.compose.material3.RadioButton(presentation == hiddenPresentation, { onHiddenPresentation(presentation) }) },
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_content_warning_expand_all)) },
            supportingContent = { Text(stringResource(R.string.settings_content_warning_expand_all_summary)) },
            trailingContent = { Switch(rules.expandAll, { onChanged(rules.copy(expandAll = it)) }) },
        )
        Text(stringResource(R.string.settings_content_warning_rules_summary), Modifier.padding(20.dp))
        RuleField(stringResource(R.string.settings_content_warning_hide_keywords), hideKeywords) { hideKeywords = it }
        RuleField(stringResource(R.string.settings_content_warning_hide_hashtags), hideHashtags) { hideHashtags = it }
        RuleField(stringResource(R.string.settings_content_warning_expand_keywords), expandKeywords) { expandKeywords = it }
        RuleField(stringResource(R.string.settings_content_warning_expand_hashtags), expandHashtags) { expandHashtags = it }
        Button(
            onClick = {
                onChanged(
                    rules.copy(
                        hideKeywords = hideKeywords.split(","),
                        hideHashtags = hideHashtags.split(","),
                        expandKeywords = expandKeywords.split(","),
                        expandHashtags = expandHashtags.split(","),
                    ).normalized(),
                )
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        ) { Text(stringResource(R.string.settings_content_warning_save)) }
        Text(stringResource(R.string.settings_content_warning_account_rules, localAccountLabel ?: stringResource(R.string.settings_content_warning_current_account)), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_content_warning_account_hide_all)) },
            trailingContent = { Switch(localRules.hideAll, { onLocalChanged(localRules.copy(hideAll = it)) }) },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_content_warning_account_expand_all)) },
            trailingContent = { Switch(localRules.expandAll, { onLocalChanged(localRules.copy(expandAll = it)) }) },
        )
        RuleField(stringResource(R.string.settings_content_warning_account_hide_keywords), localHideKeywords) { localHideKeywords = it }
        RuleField(stringResource(R.string.settings_content_warning_account_hide_hashtags), localHideHashtags) { localHideHashtags = it }
        RuleField(stringResource(R.string.settings_content_warning_account_expand_keywords), localExpandKeywords) { localExpandKeywords = it }
        RuleField(stringResource(R.string.settings_content_warning_account_expand_hashtags), localExpandHashtags) { localExpandHashtags = it }
        Button(
            onClick = {
                onLocalChanged(localRules.copy(
                    hideKeywords = localHideKeywords.split(","),
                    hideHashtags = localHideHashtags.split(","),
                    expandKeywords = localExpandKeywords.split(","),
                    expandHashtags = localExpandHashtags.split(","),
                ).normalized())
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        ) { Text(stringResource(R.string.settings_content_warning_save_account)) }
    }
}

@Composable
private fun RuleField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.settings_content_warning_comma_hint)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        singleLine = true,
    )
}
