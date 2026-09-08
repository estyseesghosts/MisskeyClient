@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.sharePost

@Composable
fun MediaViewerScreen(
    request: MediaOpenRequest,
    onClose: () -> Unit,
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
) {
    val attachments = request.ownedPost.post.attachments
    val context = LocalContext.current
    val pagerState = rememberPagerState(request.attachmentIndex.coerceIn(0, attachments.lastIndex)) { attachments.size }
    val revealedPages = remember { mutableStateMapOf<Int, Boolean>() }
    var menuVisible by rememberSaveable { mutableStateOf(false) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize().semantics { contentDescription = "Media viewer" }) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                val attachment = attachments[page]
                MediaPage(
                    attachment = attachment,
                    index = page,
                    selected = page == pagerState.settledPage,
                    revealed = !attachment.sensitive || revealedPages[page] == true,
                    accountIdentity = request.ownedPost.fetchedBy.toString(),
                    postIdentity = "${request.ownedPost.post.id.connection}/${request.ownedPost.post.id.value}",
                    onReveal = { revealedPages[page] = true },
                )
            }
            if (chromeVisible) {
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Close media viewer" }) { Icon(AppIcons.Close, null, tint = Color.White) }
                    Box {
                        IconButton(onClick = { menuVisible = true }, modifier = Modifier.semantics { contentDescription = "Media options" }) { Icon(AppIcons.More, null, tint = Color.White) }
                        DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                            DropdownMenuItem(text = { Text("Open media in browser") }, onClick = { menuVisible = false })
                            request.ownedPost.post.attachments[pagerState.settledPage].description?.let {
                                DropdownMenuItem(text = { Text("Description") }, onClick = { menuVisible = false })
                            }
                        }
                    }
                }
                Text(
                    "${pagerState.settledPage + 1} / ${attachments.size}",
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 20.dp),
                    color = Color.White,
                )
                Row(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ViewerAction(AppIcons.Heart, "Favorite") { onReact(request.ownedPost) }
                    ViewerAction(AppIcons.Reply, "Reply") { onReply(request.ownedPost) }
                    ViewerAction(AppIcons.Repost, "Repost") { onReshare(request.ownedPost) }
                    ViewerAction(AppIcons.Share, "Share") { sharePost(context, request.ownedPost.post) }
                }
            }
        }
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Icon(icon, label, tint = Color.White)
    }
}
