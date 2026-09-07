package me.foxtails.palustris.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Small, bundled vector set; no font downloads or remote assets. */
object AppIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd).build()
    val Home = icon("Home", "M12,3 2,12h3v9h6v-6h2v6h6v-9h3z M12,5.7 17,10.2V19h-2v-6H9v6H7v-8.8z")
    val Search = icon("Search", "M9.5,3a6.5,6.5 0,1 0,4.03 11.6L20.4,21l1.4,-1.4 -6.87,-6.4A6.5,6.5 0,0 0,9.5 3z M9.5,5a4.5,4.5 0,1 1,0 9a4.5,4.5 0,0 1,0 -9")
    val WaffleGrid = icon("WaffleGrid", "M4,4h4v4H4z M10,4h4v4h-4z M16,4h4v4h-4z M4,10h4v4H4z M10,10h4v4h-4z M16,10h4v4h-4z M4,16h4v4H4z M10,16h4v4h-4z M16,16h4v4h-4z")
    val Notifications = icon("Notifications", "M12,2a2,2 0,0 0,-2 2v.35A6,6 0,0 0,6 10v6l-2,2v1h16v-1l-2,-2v-6a6,6 0,0 0,-4 -5.65V4a2,2 0,0 0,-2 -2z M12,6a4,4 0,0 1,4 4v7H8v-7a4,4 0,0 1,4 -4z M10,20a2,2 0,0 0,4 0z")
    val Person = icon("Person", "M12,3a4,4 0,1 0,0 8a4,4 0,0 0,0 -8z M12,5a2,2 0,1 1,0 4a2,2 0,0 1,0 -4z M12,13c-4,0 -8,2 -8,5v3h16v-3c0,-3 -4,-5 -8,-5z M12,15c3,0 6,1.5 6,3v1H6v-1c0,-1.5 3,-3 6,-3z")
    val Edit = icon("Edit", "M3,17.25V21h3.75L17.81,9.94 14.06,6.19z M20.71,7.04a1,1 0,0 0,0 -1.42l-2.34,-2.34a1,1 0,0 0,-1.42 0l-1.83,1.83 3.75,3.75z")
    val Compose = icon("Compose", "M3,17.25V21h3.75L17.81,9.94 14.06,6.19z M20.71,7.04a1,1 0,0 0,0 -1.42l-2.34,-2.34a1,1 0,0 0,-1.42 0l-1.83,1.83 3.75,3.75z")
    val PersonEdit = icon("PersonEdit", "M12,3a4,4 0,1 0,0 8a4,4 0,0 0,0 -8z M12,5a2,2 0,1 1,0 4a2,2 0,0 1,0 -4z M4,20v-2c0,-3 4,-5 8,-5c1.5,0 2.9,.25 4.15,.7l-1.7,1.7c-.75,-.18 -1.57,-.28 -2.45,-.28c-3,0 -6,1.5 -6,3v.88h6.88L11,20z M17.4,14.1l2.5,2.5 -5.9,5.9H11.5V20z M19.2,12.3l1.1,1.1 -2.5,2.5 -1.1,-1.1z")
    val Unavailable = icon("Unavailable", "M12,2a10,10 0,1 0,0 20a10,10 0,0 0,0 -20z M6.2,7.6 16.4,17.8 17.8,16.4 7.6,6.2z")
    val Bookmark = icon("Bookmark", "M6,3v18l6,-3 6,3V3z M8,5h8v12.76l-4,-2 -4,2z")
    val Folder = icon("Folder", "M3,4h7l2,2h9v14H3z M5,6v12h14V8h-8l-2,-2z")
    val More = icon("More", "M12,3a2,2 0,1 0,0 4a2,2 0,0 0,0 -4 M12,10a2,2 0,1 0,0 4a2,2 0,0 0,0 -4 M12,17a2,2 0,1 0,0 4a2,2 0,0 0,0 -4")
    val Expand = icon("Expand", "M6.4,8.6 12,14.2 17.6,8.6 19,10 12,17 5,10z")
    val Back = icon("Back", "M20,11H7.83l5.59,-5.59L12,4 4,12l8,8 1.42,-1.41L7.83,13H20z")
    val Close = icon("Close", "M6.4,5 12,10.6 17.6,5 19,6.4 13.4,12 19,17.6 17.6,19 12,13.4 6.4,19 5,17.6 10.6,12 5,6.4z")
    val Globe = icon("Globe", "M12,2a10,10 0,1 0,0 20a10,10 0,0 0,0 -20z M4.3,10H8v4H4.3a8,8 0,0 1,0 -4z M6,6.7 8.9,4.6 8.2,8H5.1z M10,10h4v4h-4z M10.2,8 12,4 13.8,8z M15.8,8l-.7,-3.4L18.9,8z M16,10h3.7a8,8 0,0 1,0 4H16z M15.8,16h3.1l-3.8,3.4z M13.8,16 12,20 10.2,16z M8.2,16l.7,3.4L5.1,16z")
    val Check = icon("Check", "M9,16.2 4.8,12 3.4,13.4 9,19 21,7 19.6,5.6z")
    val Chat = icon("Chat", "M3,3h18v15H8l-5,4z M5,5v12.2L7.3,16H19V5z")
    val Reply = icon("Reply", "M4,3h9a5,5 0,0 1,5 5v4a5,5 0,0 1,-5 5H9l-5,3v-3H4a5,5 0,0 1,-5 -5V8a5,5 0,0 1,5 -5z M4,5a3,3 0,0 0,-3 3v4a3,3 0,0 0,3 3h1v1.47L8.45,15H13a3,3 0,0 0,3 -3V8a3,3 0,0 0,-3 -3z M14,8h4a5,5 0,0 1,5 5v4a5,5 0,0 1,-5 5h-1v-2h1a3,3 0,0 0,3 -3v-4a3,3 0,0 0,-3 -3h-4z")
    val Repost = icon("Repost", "M7,22 3,18 7,14 8.4,15.45 6.85,17H17v-4h2v6H6.85l1.55,1.55z M5,11V5h12.15L15.6,3.45 17,2l4,4 -4,4 -1.4,-1.45L17.15,7H7v4z")
    val Heart = icon("Heart", "M16.5,3c-1.74,0 -3.41,0.81 -4.5,2.09C10.91,3.81 9.24,3 7.5,3 4.42,3 2,5.42 2,8.5c0,3.76 3.4,6.86 8.55,11.54L12,21.35l1.45,-1.32C18.6,15.36 22,12.26 22,8.5 22,5.42 19.58,3 16.5,3z M12.1,18.55l-.1,.1 -.1,-.1C7.14,14.24 4,11.39 4,8.5 4,6.5 5.5,5 7.5,5c1.54,0 3.04,.99 3.57,2.36h1.87C13.46,5.99 14.96,5 16.5,5c2,0 3.5,1.5 3.5,3.5 0,2.89 -3.14,5.74 -7.9,10.05z")
    val Share = icon("Share", "M14,3h7v7h-2V6.4l-7.3,7.3 -1.4,-1.4L17.6,5H14z M4,5h6v2H5v12h12v-5h2v7H3V5z")
    val Image = icon("Image", "M3,3h18v18H3z M5,5v14h14V5z M6,17l4,-5 3,3 2,-2 3,4z M15,7a2,2 0,1 0,0 4a2,2 0,0 0,0 -4")
    val Tag = icon("Tag", "M9,3 8,8H3v2h4.6l-.8,4H2v2h4.4l-1,5h2l1,-5h6l-1,5h2l1,-5H21v-2h-4.2l.8,-4H22V8h-4l1,-5h-2l-1,5h-6l1,-5z M10,10h5.6l-.8,4H9.2z")
}
