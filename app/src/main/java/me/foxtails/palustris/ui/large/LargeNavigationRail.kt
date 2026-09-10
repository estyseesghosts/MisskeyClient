package me.foxtails.palustris.ui.large

import androidx.compose.foundation.combinedClickable
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AccountAvatar
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.Avatar

internal enum class LargeNavTarget(@StringRes val labelRes: Int) {
    Home(R.string.nav_home),
    Search(R.string.nav_search),
    AlternateSearch(R.string.nav_alternate_search),
    Notifications(R.string.nav_notifications),
    DirectMessages(R.string.nav_direct_messages),
    Profile(R.string.nav_profile),
}

@Composable
internal fun LargeNavigationRail(
    selectedTarget: LargeNavTarget,
    account: Account?,
    onTargetSelected: (LargeNavTarget) -> Unit,
    onOpenAccounts: () -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(80.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = AppIcons.Globe,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 8.dp).size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            LargeNavTarget.entries.filter { it != LargeNavTarget.Profile }.forEach { target ->
                val selected = selectedTarget == target
                val label = stringResource(target.labelRes)
                IconButton(
                    onClick = { onTargetSelected(target) },
                    modifier = Modifier
                        .size(56.dp)
                        .semantics {
                            contentDescription = label
                            this.selected = selected
                            role = Role.Tab
                        },
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    ) {
                        Icon(
                            imageVector = when (target) {
                                LargeNavTarget.Home -> AppIcons.Home
                                LargeNavTarget.Search -> AppIcons.Search
                                LargeNavTarget.AlternateSearch -> AppIcons.WaffleGrid
                                LargeNavTarget.Notifications -> AppIcons.Notifications
                                LargeNavTarget.DirectMessages -> AppIcons.Chat
                                LargeNavTarget.Profile -> AppIcons.Person
                            },
                            contentDescription = null,
                            modifier = Modifier.padding(14.dp),
                            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f, fill = false))
            val currentAccountDescription = stringResource(R.string.a11y_current_account)
            val avatarModifier = Modifier
                .size(52.dp)
                .combinedClickable(
                    onClick = { onTargetSelected(LargeNavTarget.Profile) },
                    onLongClick = onOpenAccounts,
                    onLongClickLabel = stringResource(R.string.nav_switch_account),
                )
                .semantics {
                    contentDescription = currentAccountDescription
                    this.selected = selectedTarget == LargeNavTarget.Profile
                    role = Role.Tab
                }
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.large,
                color = if (selectedTarget == LargeNavTarget.Profile) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (account != null) AccountAvatar(account, avatarModifier) else Avatar(avatarModifier)
                }
            }
            FloatingActionButton(onClick = onCompose, modifier = Modifier.size(52.dp)) {
                Icon(AppIcons.Edit, stringResource(R.string.nav_compose))
            }
        }
    }
}
