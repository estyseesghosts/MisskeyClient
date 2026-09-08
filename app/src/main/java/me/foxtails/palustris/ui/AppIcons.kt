package me.foxtails.palustris.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/** Bundled application icons, using the provided traced artwork where available. */
object AppIcons {
    private const val SvgViewport = 2048f
    private const val SvgContentSize = 1728f

    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd).build()
    private fun tracedIcon(name: String, path: String) = tracedIcon(name, listOf(path))
    private fun tracedIcon(name: String, paths: List<String>): ImageVector {
        val parsedPaths = paths.map { pathString ->
            val parser = PathParser().parsePathString(pathString)
            ParsedPath(parser.toNodes(), parser.toPath(Path()).getBounds())
        }
        val left = parsedPaths.minOf { it.bounds.left }
        val top = parsedPaths.minOf { it.bounds.top }
        val right = parsedPaths.maxOf { it.bounds.right }
        val bottom = parsedPaths.maxOf { it.bounds.bottom }
        val width = (right - left).coerceAtLeast(1f)
        val height = (bottom - top).coerceAtLeast(1f)
        val scale = minOf(SvgContentSize / width, SvgContentSize / height)
        val translationX = (SvgViewport - width * scale) / 2f - left * scale
        val translationY = (SvgViewport - height * scale) / 2f - top * scale

        return ImageVector.Builder(name, 24.dp, 24.dp, SvgViewport, SvgViewport).apply {
            group(scaleX = scale, scaleY = scale, translationX = translationX, translationY = translationY) {
                parsedPaths.forEach { parsedPath ->
                    addPath(
                        parsedPath.nodes,
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.EvenOdd,
                    )
                }
            }
        }.build()
    }

    private data class ParsedPath(
        val nodes: List<PathNode>,
        val bounds: Rect,
    )

    val Home = tracedIcon("Home", SvgIconPaths.Home)
    val Search = tracedIcon("Search", SvgIconPaths.Search)
    val WaffleGrid = tracedIcon("WaffleGrid", SvgIconPaths.WaffleGrid)
    val Notifications = tracedIcon("Notifications", SvgIconPaths.Notifications)
    val Person = tracedIcon("Person", SvgIconPaths.Person)
    val Edit = tracedIcon("Edit", SvgIconPaths.Edit)
    val Compose = tracedIcon("Compose", SvgIconPaths.Edit)
    val PersonEdit = tracedIcon("PersonEdit", SvgIconPaths.PersonEdit)
    val Unavailable = tracedIcon("Unavailable", SvgIconPaths.Close)
    val Bookmark = tracedIcon("Bookmark", SvgIconPaths.Bookmark)
    val Folder = tracedIcon("Folder", SvgIconPaths.Folder)
    val More = tracedIcon("More", SvgIconPaths.Expand)
    val Expand = tracedIcon("Expand", SvgIconPaths.Expand)
    val Back = tracedIcon("Back", SvgIconPaths.Back)
    val Close = tracedIcon("Close", SvgIconPaths.Close)
    val Globe = tracedIcon("Globe", SvgIconPaths.Globe)
    val Check = tracedIcon("Check", SvgIconPaths.Check)
    val Chat = tracedIcon("Chat", SvgIconPaths.Chat)
    val Reply = tracedIcon("Reply", SvgIconPaths.Reply)
    val Repost = tracedIcon("Repost", SvgIconPaths.Repost)
    val Heart = tracedIcon("Heart", SvgIconPaths.Heart)
    val Share = tracedIcon("Share", SvgIconPaths.Share)
    val Image = tracedIcon("Image", SvgIconPaths.Image)
    val Tag = icon("Tag", "M9,3 8,8H3v2h4.6l-.8,4H2v2h4.4l-1,5h2l1,-5h6l-1,5h2l1,-5H21v-2h-4.2l.8,-4H22V8h-4l1,-5h-2l-1,5h-6l1,-5z M10,10h5.6l-.8,4H9.2z")
}
