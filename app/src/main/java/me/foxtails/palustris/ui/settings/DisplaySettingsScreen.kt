package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.AppBackground
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.AppTextSize

@Composable
fun DisplaySettingsScreen(
    preferences: AppPreferences,
    onColorScheme: (AppColorScheme) -> Unit,
    onBackground: (AppBackground) -> Unit,
    onTextSize: (AppTextSize) -> Unit,
    onFont: (AppFont) -> Unit,
    onRequest60Hz: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        ChoiceGroup("Colour scheme", AppColorScheme.entries, preferences.colorScheme, onColorScheme) { it.name }
        HorizontalDivider()
        ChoiceGroup("Background", AppBackground.entries, preferences.background, onBackground) { it.name.replace("PureBlack", "Pure black") }
        Text(stringResource(R.string.settings_pure_black_warning), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        HorizontalDivider()
        ChoiceGroup("Text size", AppTextSize.entries, preferences.textSize, onTextSize) { it.name }
        HorizontalDivider()
        ChoiceGroup("Font", AppFont.entries, preferences.font, onFont) { it.name }
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_request_60hz)) },
            supportingContent = { Text(stringResource(R.string.settings_request_60hz_summary)) },
            trailingContent = { Switch(checked = preferences.request60Hz, onCheckedChange = onRequest60Hz) },
        )
    }
}

@Composable
private fun <T> ChoiceGroup(title: String, values: List<T>, selected: T, onSelected: (T) -> Unit, label: (T) -> String) {
    Text(title, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    values.forEach { value ->
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            headlineContent = { Text(label(value)) },
            leadingContent = { RadioButton(selected = value == selected, onClick = { onSelected(value) }) },
        )
    }
}
