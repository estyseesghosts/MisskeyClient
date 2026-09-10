package me.foxtails.palustris.ui.large

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.ui.AccountAvatar
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.Avatar

internal enum class LargeNavTarget(val label: String) {
    Home("Home"),
    Search("Search"),
    AlternateSearch("Alt Source"),
    Notifications("Notifications"),
    DirectMessages("Private Messages"),
    Profile("Profile"),
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
            LargeNavTarget.entries.forEach { target ->
                val selected = selectedTarget == target
                IconButton(
                    onClick = { onTargetSelected(target) },
                    modifier = Modifier
                        .size(56.dp)
                        .semantics {
                            contentDescription = target.label
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
            val avatarModifier = Modifier
                .size(52.dp)
                .combinedClickable(
                    onClick = { onTargetSelected(LargeNavTarget.Profile) },
                    onLongClick = onOpenAccounts,
                    onLongClickLabel = "Switch account",
                )
                .semantics { contentDescription = "Current account; long press to switch account" }
            if (account != null) AccountAvatar(account, avatarModifier) else Avatar(avatarModifier)
            FloatingActionButton(onClick = onCompose, modifier = Modifier.size(52.dp)) {
                Icon(AppIcons.Edit, "Compose post")
            }
        }
    }
}
