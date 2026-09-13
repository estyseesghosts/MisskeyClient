package me.foxtails.palustris.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
fun AppBackHandler(state: BackNavigationState) {
    BackHandler(enabled = state.dismissible, onBack = state.dismiss)
}
