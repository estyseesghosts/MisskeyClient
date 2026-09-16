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
                        LocalPage.Drafts -> stringResource(R.string.drafts_page_title)
                        LocalPage.About -> stringResource(R.string.about_page_title)
                    },
                )
            },
            navigationIcon = { ActionIcon(AppIcons.Back, stringResource(R.string.app_back), onBack) },
        )
        notificationRoute != null -> TopAppBar(
            title = { Text(stringResource(R.string.app_notification)) },
            navigationIcon = { ActionIcon(AppIcons.Back, stringResource(R.string.app_back), onBack) },
        )
    }
}
