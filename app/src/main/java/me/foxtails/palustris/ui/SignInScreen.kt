package me.foxtails.palustris.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress

private val suggestedInstances = listOf(
    "misskey.io" to "Misskey", "misskey.design" to "Misskey", "misskey.art" to "Misskey",
    "sharkey.world" to "Sharkey", "federation.network" to "Sharkey", "sakurajima.social" to "Sharkey",
)

@Composable
fun SignInScreen(
    state: SessionUi,
    onNext: (String) -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    var domain by rememberSaveable { mutableStateOf("") }
    val scheme = LocalPalustrisMotionScheme.current
    Scaffold(
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { if (state.pending) onComplete() else onNext(domain) }, enabled = !state.busy && (domain.isNotBlank() || state.pending), modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().height(56.dp)) {
                        AnimatedContent(
                            targetState = state.busy to state.pending,
                            transitionSpec = { if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None else fadeIn(scheme.fastFadeIn) togetherWith fadeOut(scheme.fastFadeOut) },
                            label = "signInButtonContent",
                        ) { (busy, pending) -> if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(if (pending) R.string.sign_in_button_authorized else R.string.sign_in_button_next)) }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(32.dp))
                Text(stringResource(R.string.app_name), Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.headlineLarge)
                TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.settings_title)) }
                Spacer(Modifier.height(40.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) { Icon(AppIcons.Globe, null, Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.signin_fediverse_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.signin_fediverse_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
                AnimatedStatePane(stateKey = state.pending, modifier = Modifier.fillMaxWidth()) {
                    Text(when { state.pending -> stringResource(R.string.sign_in_pending_title); state.addingAccount -> stringResource(R.string.sign_in_add_account_title); else -> stringResource(R.string.sign_in_title) }, style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(if (state.pending) stringResource(R.string.sign_in_pending_description, stringResource(R.string.app_name), state.origin?.removePrefix("https://").orEmpty()) else if (state.addingAccount) stringResource(R.string.sign_in_add_account_description) else stringResource(R.string.sign_in_description), style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(24.dp))
                if (!state.pending) {
                    OutlinedTextField(value = domain, onValueChange = { domain = it }, enabled = !state.busy, label = { Text(stringResource(R.string.sign_in_instance_url)) }, placeholder = { Text(stringResource(R.string.sign_in_instance_placeholder)) }, leadingIcon = { Icon(AppIcons.Globe, null) }, singleLine = true, shape = CircleShape, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(28.dp))
                    Text(stringResource(R.string.sign_in_popular_instances), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.sign_in_choose_instance), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    suggestedInstances.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (host, software) ->
                                val interactionSource = remember(host) { MutableInteractionSource() }
                                val selected = domain == host
                                OutlinedButton(onClick = { domain = host }, enabled = !state.busy, interactionSource = interactionSource, modifier = Modifier.weight(1f).springPress(interactionSource), shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AnimatedContent(targetState = selected, transitionSpec = { if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None else fadeIn(scheme.fastFadeIn) togetherWith fadeOut(scheme.fastFadeOut) }, label = "suggestedInstanceSelection") { isSelected -> if (isSelected) Icon(AppIcons.Check, null, Modifier.size(16.dp)) }
                                        Spacer(Modifier.width(if (selected) 4.dp else 0.dp))
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(host, style = MaterialTheme.typography.labelLarge); Text(software, style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    OutlinedButton(onClick = onReopen, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sign_in_open_browser_again)) }
                    TextButton(onClick = onCancel, enabled = !state.busy) { Text(stringResource(if (state.addingAccount) R.string.sign_in_cancel else R.string.sign_in_different_instance)) }
                }
                AnimatedStatePane(stateKey = state.error != null, modifier = Modifier.fillMaxWidth()) { state.error?.let { Spacer(Modifier.height(16.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
