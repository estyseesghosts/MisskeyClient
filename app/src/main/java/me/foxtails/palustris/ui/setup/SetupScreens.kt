package me.foxtails.palustris.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.data.misskey.ServerAddress
import me.foxtails.palustris.ui.session.SessionUi

@Composable
internal fun SetupInitialScreen(onNewUser: () -> Unit, onSignIn: () -> Unit) {
    SetupColumn {
        LogoSurface(Modifier.fillMaxWidth().weight(1f, fill = false))
        Spacer(Modifier.height(12.dp))
        SetupPrimaryAction(stringResource(R.string.setup_new_user), onNewUser)
        Spacer(Modifier.height(12.dp))
        SetupSecondaryAction(stringResource(R.string.setup_sign_in), onSignIn)
    }
}

@Composable
internal fun SetupIntroductionScreen(onGetStarted: () -> Unit) {
    SetupColumn {
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            shape = RoundedCornerShape(48.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.setup_welcome_to), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.setup_the_fediverse), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.beeline_mark),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(.65f),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SetupPrimaryAction(stringResource(R.string.setup_get_started), onGetStarted)
    }
}

@Composable
internal fun SetupServerScreen(
    state: SessionUi,
    onNext: (String) -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onCancel: () -> Unit,
) {
    var server by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val whitespaceError = stringResource(R.string.setup_server_whitespace_error)
    val submit: () -> Unit = {
        if (state.pending) {
            onComplete()
        } else {
            try {
                val normalized = ServerAddress.normalize(server)
                validationError = null
                onNext(normalized)
            } catch (error: IllegalArgumentException) {
                validationError = error.message ?: "Enter a valid HTTPS server."
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Text(
            stringResource(if (state.pending) R.string.sign_in_pending_title else R.string.setup_welcome_back),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!state.pending) {
            Text(
                stringResource(R.string.setup_heart),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Start),
            )
        }
        Spacer(Modifier.weight(1f, fill = true))
        if (state.pending) {
            Text(
                stringResource(
                    R.string.sign_in_pending_description,
                    stringResource(R.string.app_name),
                    state.origin?.removePrefix("https://").orEmpty(),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))
            SetupPrimaryAction(stringResource(R.string.sign_in_button_authorized), onComplete, enabled = !state.busy)
            Spacer(Modifier.height(12.dp))
            SetupSecondaryAction(stringResource(R.string.sign_in_open_browser_again), onReopen, enabled = !state.busy)
            Spacer(Modifier.height(8.dp))
            SetupTextAction(stringResource(if (state.addingAccount) R.string.sign_in_cancel else R.string.sign_in_different_instance), onCancel, enabled = !state.busy)
        } else {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                validationError?.let {
                    Text(it, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), color = MaterialTheme.colorScheme.error)
                }
                SetupServerField(server, !state.busy, submit) { value ->
                    val trimmed = value.trim()
                    if (trimmed.any(Char::isWhitespace)) {
                        validationError = whitespaceError
                    } else {
                        server = trimmed
                        validationError = null
                    }
                }
                Spacer(Modifier.height(12.dp))
                SetupSecondaryAction(
                    label = stringResource(R.string.setup_next),
                    onClick = submit,
                    enabled = server.isNotBlank() && !state.busy,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SetupColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

@Composable
private fun LogoSurface(modifier: Modifier) {
    Surface(
        modifier = modifier.height(520.dp),
        shape = RoundedCornerShape(48.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.setup_wordmark), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.beeline_mark),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(.65f),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SetupPrimaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp).widthIn(max = 480.dp),
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun SetupSecondaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .widthIn(max = 480.dp)
            .clip(RoundedCornerShape(percent = 50))
            .semantics { role = Role.Button },
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) { Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) } }
}

@Composable
private fun SetupTextAction(label: String, onClick: () -> Unit, enabled: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = label; role = Role.Button },
        onClick = onClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.bodyLarge) } }
}

@Composable
private fun SetupServerField(
    value: String,
    enabled: Boolean,
    onSubmit: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onPrimary),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = Modifier.fillMaxWidth().height(64.dp).widthIn(max = 480.dp).testTag("setup_server_field"),
        decorationBox = { field ->
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) Text(stringResource(R.string.setup_server_placeholder), style = MaterialTheme.typography.labelLarge)
                    field()
                }
            }
        },
    )
}
