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
    val Notifications = icon("Notifications", "M12,2a2,2 0,0 0,-2 2v.35A6,6 0,0 0,6 10v6l-2,2v1h16v-1l-2,-2v-6a6,6 0,0 0,-4 -5.65V4a2,2 0,0 0,-2 -2z M12,6a4,4 0,0 1,4 4v7H8v-7a4,4 0,0 1,4 -4z M10,20a2,2 0,0 0,4 0z")
    val Person = icon("Person", "M12,3a4,4 0,1 0,0 8a4,4 0,0 0,0 -8z M12,5a2,2 0,1 1,0 4a2,2 0,0 1,0 -4z M12,13c-4,0 -8,2 -8,5v3h16v-3c0,-3 -4,-5 -8,-5z M12,15c3,0 6,1.5 6,3v1H6v-1c0,-1.5 3,-3 6,-3z")
    val Edit = icon("Edit", "M3,17.25V21h3.75L17.81,9.94 14.06,6.19z M20.71,7.04a1,1 0,0 0,0 -1.42l-2.34,-2.34a1,1 0,0 0,-1.42 0l-1.83,1.83 3.75,3.75z")
    val Bookmark = icon("Bookmark", "M6,3v18l6,-3 6,3V3z M8,5h8v12.76l-4,-2 -4,2z")
    val Folder = icon("Folder", "M3,4h7l2,2h9v14H3z M5,6v12h14V8h-8l-2,-2z")
    val More = icon("More", "M12,3a2,2 0,1 0,0 4a2,2 0,0 0,0 -4 M12,10a2,2 0,1 0,0 4a2,2 0,0 0,0 -4 M12,17a2,2 0,1 0,0 4a2,2 0,0 0,0 -4")
    val Expand = icon("Expand", "M6.4,8.6 12,14.2 17.6,8.6 19,10 12,17 5,10z")
    val Back = icon("Back", "M20,11H7.83l5.59,-5.59L12,4 4,12l8,8 1.42,-1.41L7.83,13H20z")
    val Close = icon("Close", "M6.4,5 12,10.6 17.6,5 19,6.4 13.4,12 19,17.6 17.6,19 12,13.4 6.4,19 5,17.6 10.6,12 5,6.4z")
    val Globe = icon("Globe", "M12,2a10,10 0,1 0,0 20a10,10 0,0 0,0 -20z M4.3,10H8v4H4.3a8,8 0,0 1,0 -4z M6,6.7 8.9,4.6 8.2,8H5.1z M10,10h4v4h-4z M10.2,8 12,4 13.8,8z M15.8,8l-.7,-3.4L18.9,8z M16,10h3.7a8,8 0,0 1,0 4H16z M15.8,16h3.1l-3.8,3.4z M13.8,16 12,20 10.2,16z M8.2,16l.7,3.4L5.1,16z")
    val Check = icon("Check", "M9,16.2 4.8,12 3.4,13.4 9,19 21,7 19.6,5.6z")
    val Chat = icon("Chat", "M3,3h18v15H8l-5,4z M5,5v12.2L7.3,16H19V5z")
    val Image = icon("Image", "M3,3h18v18H3z M5,5v14h14V5z M6,17l4,-5 3,3 2,-2 3,4z M15,7a2,2 0,1 0,0 4a2,2 0,0 0,0 -4")
    val Tag = icon("Tag", "M9,3 8,8H3v2h4.6l-.8,4H2v2h4.4l-1,5h2l1,-5h6l-1,5h2l1,-5H21v-2h-4.2l.8,-4H22V8h-4l1,-5h-2l-1,5h-6l1,-5z M10,10h5.6l-.8,4H9.2z")
}
