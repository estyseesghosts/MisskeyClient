package me.foxtails.palustris.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.setup.SetupInitialScreen
import me.foxtails.palustris.ui.setup.SetupIntroductionScreen
import me.foxtails.palustris.ui.setup.SetupServerScreen
import me.foxtails.palustris.ui.setup.setupTypography

private enum class SetupDestination { Initial, Server, Pending }

@Composable
fun SignInScreen(
    state: SessionUi,
    onNext: (String) -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    var destination by remember {
        mutableStateOf(if (state.pending) SetupDestination.Pending else if (state.addingAccount) SetupDestination.Server else SetupDestination.Initial)
    }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val motion = LocalPalustrisMotionScheme.current
    LaunchedEffect(state.pending, state.addingAccount) {
        destination = when {
            state.pending -> SetupDestination.Pending
            state.addingAccount -> SetupDestination.Server
            else -> destination
        }
    }
    BackHandler(enabled = destination != SetupDestination.Initial) {
        if (state.pending) {
            onCancel()
        } else {
            focusManager.clearFocus()
            destination = SetupDestination.Initial
        }
    }
    MaterialTheme(typography = setupTypography(MaterialTheme.typography)) {
        AnimatedContent(
            targetState = destination,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (motion.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                else fadeIn(motion.fastFadeIn) togetherWith fadeOut(motion.fastFadeOut)
            },
            label = "setupDestination",
        ) { screen ->
            when (screen) {
                SetupDestination.Initial -> SetupInitialScreen(
                    onNewUser = {
                        Toast.makeText(context, context.getString(R.string.setup_not_ready), Toast.LENGTH_SHORT).show()
                    },
                    onSignIn = { destination = SetupDestination.Server },
                )
                SetupDestination.Server, SetupDestination.Pending -> SetupServerScreen(
                    state = state,
                    onNext = onNext,
                    onComplete = onComplete,
                    onReopen = onReopen,
                    onCancel = {
                        onCancel()
                        destination = SetupDestination.Initial
                    },
                )
            }
        }
    }
}

@Composable
fun SetupIntroductionPreview(onGetStarted: () -> Unit = {}) {
    MaterialTheme(typography = setupTypography(MaterialTheme.typography)) {
        SetupIntroductionScreen(onGetStarted)
    }
}
