package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AppLanguage

/** Every catalog language stays reachable on compact screens. One row is selected. */
@Composable
fun LanguageSettingsScreen(selected: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().selectableGroup()) {
        items(AppLanguage.entries, key = { it.name }) { language ->
            val headline =
                if (language == AppLanguage.SystemDefault) stringResource(R.string.language_system_default)
                else language.label()
            val supporting =
                language.tag ?: stringResource(R.string.language_system_default_description)
            ListItem(
                headlineContent = { Text(headline) },
                supportingContent = { Text(supporting) },
                leadingContent = {
                    RadioButton(
                        selected = language == selected,
                        onClick = null,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                },
                modifier = Modifier.selectable(
                    selected = language == selected,
                    onClick = { onSelected(language) },
                    role = Role.RadioButton,
                ),
            )
        }
    }
}

private fun AppLanguage.label(): String = when (this) {
    AppLanguage.SystemDefault -> "System default"
    AppLanguage.English -> "English"
    AppLanguage.German -> "Deutsch"
    AppLanguage.Spanish -> "Español"
    AppLanguage.SpanishSpain -> "Español (España)"
    AppLanguage.SpanishLatinAmerica -> "Español (Latinoamérica)"
    AppLanguage.French -> "Français"
    AppLanguage.Hindi -> "हिन्दी"
    AppLanguage.Japanese -> "日本語"
    AppLanguage.Korean -> "한국어"
    AppLanguage.PortugueseBrazil -> "Português (Brasil)"
    AppLanguage.PortuguesePortugal -> "Português (Portugal)"
    AppLanguage.CantoneseHongKong -> "粵語（香港）"
    AppLanguage.Chinese -> "中文"
    AppLanguage.ChineseMainland -> "简体中文（中国大陆）"
    AppLanguage.ChineseTaiwan -> "繁體中文（臺灣）"
}
