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

    val Home = tracedIcon("Home", SvgIconPaths.Home)
    val Search = tracedIcon("Search", SvgIconPaths.Search)
    val WaffleGrid = tracedIcon("WaffleGrid", SvgIconPaths.WaffleGrid)
    val Notifications = tracedIcon("Notifications", SvgIconPaths.Notifications)
    val Person = icon("Person", "M12,3a4,4 0,1 0,0 8a4,4 0,0 0,0 -8z M12,5a2,2 0,1 1,0 4a2,2 0,0 1,0 -4z M12,13c-4,0 -8,2 -8,5v3h16v-3c0,-3 -4,-5 -8,-5z M12,15c3,0 6,1.5 6,3v1H6v-1c0,-1.5 3,-3 6,-3z")
    val Edit = tracedIcon("Edit", SvgIconPaths.Edit)
    val Compose = tracedIcon("Compose", SvgIconPaths.Edit)
    val PersonEdit = tracedIcon("PersonEdit", SvgIconPaths.PersonEdit)
    val Unavailable = icon("Unavailable", "M12,2a10,10 0,1 0,0 20a10,10 0,0 0,0 -20z M6.2,7.6 16.4,17.8 17.8,16.4 7.6,6.2z")
    val Bookmark = tracedIcon("Bookmark", SvgIconPaths.Bookmark)
    val Folder = icon("Folder", "M3,4h7l2,2h9v14H3z M5,6v12h14V8h-8l-2,-2z")
    val More = icon("More", "M12,3a2,2 0,1 0,0 4a2,2 0,0 0,0 -4 M12,10a2,2 0,1 0,0 4a2,2 0,0 0,0 -4 M12,17a2,2 0,1 0,0 4a2,2 0,0 0,0 -4")
    val Expand = icon("Expand", "M6.4,8.6 12,14.2 17.6,8.6 19,10 12,17 5,10z")
    val Back = icon("Back", "M20,11H7.83l5.59,-5.59L12,4 4,12l8,8 1.42,-1.41L7.83,13H20z")
    val Close = icon("Close", "M6.4,5 12,10.6 17.6,5 19,6.4 13.4,12 19,17.6 17.6,19 12,13.4 6.4,19 5,17.6 10.6,12 5,6.4z")
    val Globe = icon("Globe", "M12,2a10,10 0,1 0,0 20a10,10 0,0 0,0 -20z M4.3,10H8v4H4.3a8,8 0,0 1,0 -4z M6,6.7 8.9,4.6 8.2,8H5.1z M10,10h4v4h-4z M10.2,8 12,4 13.8,8z M15.8,8l-.7,-3.4L18.9,8z M16,10h3.7a8,8 0,0 1,0 4H16z M15.8,16h3.1l-3.8,3.4z M13.8,16 12,20 10.2,16z M8.2,16l.7,3.4L5.1,16z")
    val Check = icon("Check", "M9,16.2 4.8,12 3.4,13.4 9,19 21,7 19.6,5.6z")
    val Chat = tracedIcon("Chat", SvgIconPaths.Chat)
    val Reply = tracedIcon("Reply", SvgIconPaths.Reply)
    val Repost = tracedIcon("Repost", SvgIconPaths.Repost)
    val Heart = tracedIcon("Heart", SvgIconPaths.Heart)
    val Share = tracedIcon("Share", SvgIconPaths.Share)
    val Image = icon("Image", "M3,3h18v18H3z M5,5v14h14V5z M6,17l4,-5 3,3 2,-2 3,4z M15,7a2,2 0,1 0,0 4a2,2 0,0 0,0 -4")
    val Tag = icon("Tag", "M9,3 8,8H3v2h4.6l-.8,4H2v2h4.4l-1,5h2l1,-5h6l-1,5h2l1,-5H21v-2h-4.2l.8,-4H22V8h-4l1,-5h-2l-1,5h-6l1,-5z M10,10h5.6l-.8,4H9.2z")
}
