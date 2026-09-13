package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.AppBackground
import me.foxtails.palustris.domain.AppColorPalette
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.AppTextSize
import me.foxtails.palustris.ui.theme.appPaletteColor

@Composable
fun DisplaySettingsScreen(
    preferences: AppPreferences,
    onColorScheme: (AppColorScheme) -> Unit,
    onColorPalette: (AppColorPalette) -> Unit,
    onBackground: (AppBackground) -> Unit,
    onTextSize: (AppTextSize) -> Unit,
    onFont: (AppFont) -> Unit,
    onRequest60Hz: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        ChoiceGroup(
            stringResource(R.string.settings_colour_style),
            AppColorScheme.entries,
            preferences.colorScheme,
            onColorScheme,
        ) { scheme ->
            stringResource(
                when (scheme) {
                    AppColorScheme.System -> R.string.settings_colour_style_system
                    AppColorScheme.SystemMonochrome -> R.string.settings_colour_style_system_monochrome
                },
            )
        }
        Text(stringResource(R.string.settings_colour_palette), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
        PaletteGrid(preferences.colorPalette, onColorPalette)
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
private fun PaletteGrid(selected: AppColorPalette, onSelected: (AppColorPalette) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppColorPalette.entries.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { palette ->
                    val label = paletteLabel(palette)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(appPaletteColor(palette), CircleShape)
                            .border(
                                width = if (palette == selected) 3.dp else 1.dp,
                                color = if (palette == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable(onClick = { onSelected(palette) })
                            .semantics {
                                contentDescription = label
                                role = Role.RadioButton
                                this.selected = palette == selected
                            }
                            .align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

@Composable
private fun paletteLabel(palette: AppColorPalette): String = stringResource(
    when (palette) {
        AppColorPalette.PastelRed -> R.string.settings_palette_pastel_red
        AppColorPalette.PastelOrange -> R.string.settings_palette_pastel_orange
        AppColorPalette.PastelYellow -> R.string.settings_palette_pastel_yellow
        AppColorPalette.PastelGreen -> R.string.settings_palette_pastel_green
        AppColorPalette.PastelBlue -> R.string.settings_palette_pastel_blue
        AppColorPalette.PastelIndigo -> R.string.settings_palette_pastel_indigo
        AppColorPalette.PastelViolet -> R.string.settings_palette_pastel_violet
        AppColorPalette.VibrantRed -> R.string.settings_palette_vibrant_red
        AppColorPalette.VibrantOrange -> R.string.settings_palette_vibrant_orange
        AppColorPalette.VibrantYellow -> R.string.settings_palette_vibrant_yellow
        AppColorPalette.VibrantGreen -> R.string.settings_palette_vibrant_green
        AppColorPalette.VibrantBlue -> R.string.settings_palette_vibrant_blue
        AppColorPalette.VibrantIndigo -> R.string.settings_palette_vibrant_indigo
        AppColorPalette.VibrantViolet -> R.string.settings_palette_vibrant_violet
    },
)

@Composable
private fun <T> ChoiceGroup(title: String, values: List<T>, selected: T, onSelected: (T) -> Unit, label: @Composable (T) -> String) {
    Text(title, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    values.forEach { value ->
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            headlineContent = { Text(label(value)) },
            leadingContent = { RadioButton(selected = value == selected, onClick = { onSelected(value) }) },
        )
    }
}
