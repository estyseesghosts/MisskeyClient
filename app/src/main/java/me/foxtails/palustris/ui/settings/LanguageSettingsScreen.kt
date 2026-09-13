package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.foxtails.palustris.domain.AppLanguage

@Composable
fun LanguageSettingsScreen(selected: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        AppLanguage.entries.forEach { language ->
            ListItem(
                headlineContent = { Text(language.label()) },
                supportingContent = { Text(language.tag ?: "Follow the device language") },
                leadingContent = { RadioButton(selected = language == selected, onClick = { onSelected(language) }) },
            )
        }
    }
}

private fun AppLanguage.label(): String = when (this) {
    AppLanguage.SystemDefault -> "System default"
    AppLanguage.English -> "English"
    AppLanguage.German -> "Deutsch"
    AppLanguage.Spanish -> "Español"
    AppLanguage.French -> "Français"
    AppLanguage.Hindi -> "हिन्दी"
    AppLanguage.Japanese -> "日本語"
    AppLanguage.Korean -> "한국어"
    AppLanguage.Chinese -> "中文"
}
