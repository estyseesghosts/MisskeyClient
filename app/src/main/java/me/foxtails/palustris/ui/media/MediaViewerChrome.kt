package me.foxtails.palustris.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.ui.AppIcons

@Composable
internal fun BoxScope.MediaViewerChrome(
    page: Int,
    pageCount: Int,
    descriptionAvailable: Boolean,
    menuVisible: Boolean,
    onMenuVisibilityChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOpenBrowser: () -> Unit,
    onShowDescription: () -> Unit,
    onReact: () -> Unit,
    onReply: () -> Unit,
    onReshare: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ChromeButton(AppIcons.Close, "Close media viewer", onClose)
        Box {
            ChromeButton(AppIcons.More, "Media options") { onMenuVisibilityChanged(true) }
            DropdownMenu(expanded = menuVisible, onDismissRequest = { onMenuVisibilityChanged(false) }) {
                DropdownMenuItem(text = { Text("Open media in browser") }, onClick = { onMenuVisibilityChanged(false); onOpenBrowser() })
                if (descriptionAvailable) DropdownMenuItem(text = { Text("Description") }, onClick = { onMenuVisibilityChanged(false); onShowDescription() })
            }
        }
    }
    Text("${page + 1} / $pageCount", Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 20.dp), color = Color.White)
    Row(
        Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ChromeButton(AppIcons.Heart, "Favorite", onReact)
        ChromeButton(AppIcons.Reply, "Reply", onReply)
        ChromeButton(AppIcons.Repost, "Repost", onReshare)
        ChromeButton(AppIcons.Share, "Share", onShare)
    }
}

@Composable
private fun ChromeButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Icon(icon, label, tint = Color.White)
    }
}
