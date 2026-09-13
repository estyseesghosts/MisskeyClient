package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R

@Composable
fun MutedHashtagsScreen(
    state: ModerationUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onAddLocalHashtag: (String) -> Unit = {},
    onRemoveLocalHashtag: (String) -> Unit = {},
) {
    var input by remember { mutableStateOf("") }
    when {
        state.loading -> Text(stringResource(R.string.settings_moderation_loading_hashtags), Modifier.padding(24.dp))
        state.localHashtagFallback -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(stringResource(R.string.settings_moderation_local_title))
            Text(
                stringResource(R.string.settings_moderation_local_summary),
                Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.settings_moderation_hashtag)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onAddLocalHashtag(input); input = "" },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text(stringResource(R.string.settings_moderation_add)) }
            }
            if (state.hashtags.isEmpty()) {
                Text(stringResource(R.string.settings_moderation_local_empty), Modifier.padding(top = 24.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    items(state.hashtags, key = { it.value }) { hashtag ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(stringResource(R.string.settings_moderation_hashtag_value, hashtag.value), Modifier.weight(1f).padding(vertical = 14.dp))
                            androidx.compose.material3.TextButton(onClick = { onRemoveLocalHashtag(hashtag.value) }) { Text(stringResource(R.string.settings_moderation_remove)) }
                        }
                    }
                }
            }
        }
        state.unsupported -> Text(stringResource(R.string.settings_moderation_unsupported), Modifier.padding(24.dp))
        state.error != null && state.hashtags.isEmpty() -> Text(state.error, Modifier.padding(24.dp))
        state.hashtags.isEmpty() -> Text(stringResource(R.string.settings_moderation_no_entries), Modifier.padding(24.dp))
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(state.hashtags, key = { it.value }) { hashtag ->
                Text(stringResource(R.string.settings_moderation_hashtag_value, hashtag.value), Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
            }
            if (state.nextCursor != null) item {
                androidx.compose.material3.TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxSize()) { Text(stringResource(R.string.settings_moderation_load_more)) }
            }
        }
    }
}
