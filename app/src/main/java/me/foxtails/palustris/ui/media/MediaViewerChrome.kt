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
import androidx.compose.ui.graphics.graphicsLayer
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alpha: Float = 1f,
) {
    Row(
        modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ChromeButton(AppIcons.Close, "Close media viewer", enabled, onClose)
        Box {
            ChromeButton(AppIcons.More, "Media options", enabled) { onMenuVisibilityChanged(true) }
            DropdownMenu(expanded = menuVisible && enabled, onDismissRequest = { onMenuVisibilityChanged(false) }) {
                DropdownMenuItem(text = { Text("Open media in browser") }, onClick = { onMenuVisibilityChanged(false); onOpenBrowser() })
                if (descriptionAvailable) DropdownMenuItem(text = { Text("Description") }, onClick = { onMenuVisibilityChanged(false); onShowDescription() })
            }
        }
    }
    Text("${page + 1} / $pageCount", modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 20.dp).graphicsLayer { this.alpha = alpha }, color = Color.White)
    Row(
        modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ChromeButton(AppIcons.Heart, "Favorite", enabled, onReact)
        ChromeButton(AppIcons.Reply, "Reply", enabled, onReply)
        ChromeButton(AppIcons.Repost, "Repost", enabled, onReshare)
        ChromeButton(AppIcons.Share, "Share", enabled, onShare)
    }
}

@Composable
private fun ChromeButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
        Icon(icon, label, tint = Color.White)
    }
}
