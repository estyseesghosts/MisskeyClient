@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.foxtails.palustris.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.navigation.AppRoute

@Composable
internal fun AppDestinationTopBar(
    page: LocalPage?,
    notificationRoute: AppRoute?,
    savedTitle: Int,
    onBack: () -> Unit,
) {
    when {
        page != null -> TopAppBar(
            title = {
                Text(
                    when (page) {
                        LocalPage.SavedPosts -> stringResource(savedTitle)
                        LocalPage.Likes -> stringResource(likedCollectionTitle())
                        else -> page.name
                    },
                )
            },
            navigationIcon = { ActionIcon(AppIcons.Back, "Back", onBack) },
        )
        notificationRoute != null -> TopAppBar(
            title = { Text(stringResource(R.string.app_notification)) },
            navigationIcon = { ActionIcon(AppIcons.Back, stringResource(R.string.app_back), onBack) },
        )
    }
}
