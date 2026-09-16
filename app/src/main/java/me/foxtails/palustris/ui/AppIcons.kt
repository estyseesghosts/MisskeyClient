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
        val fill: Color = Color.Black,
    )

    private fun tracedIconColored(name: String, paths: List<Pair<String, Color>>): ImageVector {
        val parsedPaths = paths.map { (pathString, color) ->
            val parser = PathParser().parsePathString(pathString)
            ParsedPath(parser.toNodes(), parser.toPath(Path()).getBounds(), color)
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
                        fill = SolidColor(parsedPath.fill),
                        pathFillType = PathFillType.EvenOdd,
                    )
                }
            }
        }.build()
    }

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
    val CaretDown = icon("CaretDown", "M7,9 L12,14 L17,9 Z")
    val CaretUp = icon("CaretUp", "M7,15 L12,10 L17,15 Z")
    val Pin = icon("Pin", "M15,2 L9,2 L9,8 L6,12 L6,14 L11,14 L11,22 L13,22 L13,14 L18,14 L18,12 L15,8 Z")
    val Back = tracedIcon("Back", SvgIconPaths.Back)
    val Close = tracedIcon("Close", SvgIconPaths.Close)
    val Globe = tracedIcon("Globe", SvgIconPaths.Globe)
    val Check = tracedIcon("Check", SvgIconPaths.Check)
    val Paperclip = icon("Paperclip", "M16.5,6v11.5c0,2.21-1.79,4-4,4s-4-1.79-4-4V5c0-1.66,1.34-3,3-3s3,1.34,3,3v10.5c0,.55-.45,1-1,1s-1-.45-1-1V6H11v9.5c0,1.1,.9,2,2,2s2-.9,2-2V5c0-2.21-1.79-4-4-4S7,2.79,7,5v12.5c0,3.04,2.46,5.5,5.5,5.5s5.5-2.46,5.5-5.5V6z")
    val Chat = tracedIcon("Chat", SvgIconPaths.Chat)
    val Link = icon("Link", "M10,13a5,5 0,0 0,7.1,0l2-2a5,5 0,0 0,-7.1,-7.1l-1.1,1.1 1.4,1.4 1.1,-1.1a3,3 0,0 1,4.2,4.2l-2,2a3,3 0,0 1,-4.2,0z M14,11a5,5 0,0 0,-7.1,0l-2,2A5,5 0,0 0,12,20.1l1.1,-1.1 -1.4,-1.4 -1.1,1.1a3,3 0,0 1,-4.2,-4.2l2,-2a3,3 0,0 1,4.2,0z")
    val Reply = tracedIcon("Reply", SvgIconPaths.Reply)
    val Repost = tracedIcon("Repost", SvgIconPaths.Repost)
    val Heart = tracedIcon("Heart", SvgIconPaths.Heart)
    val Share = tracedIcon("Share", SvgIconPaths.Share)
    val Image = tracedIcon("Image", SvgIconPaths.Image)
    val Tag = icon("Tag", "M9,3 8,8H3v2h4.6l-.8,4H2v2h4.4l-1,5h2l1,-5h6l-1,5h2l1,-5H21v-2h-4.2l.8,-4H22V8h-4l1,-5h-2l-1,5h-6l1,-5z M10,10h5.6l-.8,4H9.2z")

    // Supplied appsvg artwork. These are the canonical icons for the destinations and actions below.
    val HoneyHome = tracedIcon("HoneyHome", BeelineSvgPaths.Honeyhome)
    val SearchBeeline = tracedIcon("SearchBeeline", BeelineSvgPaths.SearchAll)
    val PhotoGrid = tracedIcon("PhotoGrid", BeelineSvgPaths.Image)
    val Mail = tracedIcon("Mail", BeelineSvgPaths.Mail)
    val DirectMessage = tracedIcon("DirectMessage", BeelineSvgPaths.Directmessage)
    val Comment = tracedIcon("Comment", BeelineSvgPaths.Comment)
    val ShareBeeline = tracedIcon("ShareBeeline", BeelineSvgPaths.ShareAll)
    val RepostBeeline = tracedIcon("RepostBeeline", BeelineSvgPaths.RepostAll)
    val Hashtag = tracedIcon("Hashtag", BeelineSvgPaths.Hashtag)
    val LinkBeeline = tracedIcon("LinkBeeline", BeelineSvgPaths.Link)
    val HollowStar = tracedIcon("HollowStar", BeelineSvgPaths.Hollowstar)
    val FilledStar = tracedIcon("FilledStar", BeelineSvgPaths.Filledstar)
    val HollowHeart = tracedIcon("HollowHeart", BeelineSvgPaths.Hollowheart)
    val FilledHeart = tracedIcon("FilledHeart", BeelineSvgPaths.Filledheart)
    val HollowBookmark = tracedIcon("HollowBookmark", BeelineSvgPaths.Hollowbookmark)
    val FilledBookmark = tracedIcon("FilledBookmark", BeelineSvgPaths.Filledbookmark)
    val Follow = tracedIcon("Follow", BeelineSvgPaths.FollowAll)
    val Unfollow = tracedIcon("Unfollow", BeelineSvgPaths.UnfollowAll)
    val DefaultUser = tracedIcon("DefaultUser", BeelineSvgPaths.Defaultuser)

    /**
     * Two-tone fallback avatar. The yellow backing must keep its own color,
     * so callers must draw this icon with an unspecified tint.
     */
    val DefaultUserIcon = tracedIconColored(
        "DefaultUserIcon",
        listOf(
            BeelineSvgPaths.Defaultusericon0 to Color(0xFFF1BE4C),
            BeelineSvgPaths.Defaultusericon1 to Color.Black,
        ),
    )
}
