package me.foxtails.palustris.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Bundled application icons, using the provided traced artwork where available. */
object AppIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd).build()
    private fun tracedIcon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 2048f, 2048f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd).build()
    private fun tracedIcon(name: String, paths: List<String>) = ImageVector.Builder(name, 24.dp, 24.dp, 2048f, 2048f)
        .apply { paths.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) } }.build()

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
