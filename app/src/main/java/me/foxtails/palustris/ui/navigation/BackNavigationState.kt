package me.foxtails.palustris.ui.navigation

data class BackNavigationState(
    val dismissible: Boolean,
    val dismiss: () -> Unit,
)
