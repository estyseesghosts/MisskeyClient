package me.foxtails.palustris.ui.navigation

/**
 * The top shell surface that one back press dismisses. The last value returns to Home
 * instead of dismissing a surface.
 */
enum class ShellTopSurface {
    ProfileImage,
    NotificationSettings,
    Composer,
    EditProfile,
    SinglePost,
    NotificationRoute,
    Page,
    Home,
}

/**
 * Shell back-navigation input. The shell owns the state. The policy owns the order.
 * A media viewer owns back while it is open, so no shell surface is dismissible then.
 */
data class ShellBackState(
    val mediaViewerOpen: Boolean = false,
    val profileImageOpen: Boolean = false,
    val largePresentation: Boolean = false,
    val notificationSettingsOpen: Boolean = false,
    val composerOpen: Boolean = false,
    val editProfileOpen: Boolean = false,
    val singlePostOpen: Boolean = false,
    val notificationRouteOpen: Boolean = false,
    val pageOpen: Boolean = false,
    val atHome: Boolean = true,
)

/**
 * Returns the surface that one back press dismisses, or null when back has no shell
 * target. Profile images dismiss first. Wide layouts dismiss their overlay before the
 * selected post. Local pages and notification routes dismiss before returning Home.
 */
fun topSurfaceForBack(state: ShellBackState): ShellTopSurface? {
    if (state.mediaViewerOpen) return null
    if (state.profileImageOpen) return ShellTopSurface.ProfileImage
    if (state.largePresentation) {
        if (state.notificationSettingsOpen) return ShellTopSurface.NotificationSettings
        if (state.composerOpen) return ShellTopSurface.Composer
        if (state.editProfileOpen) return ShellTopSurface.EditProfile
    }
    if (state.singlePostOpen) return ShellTopSurface.SinglePost
    if (state.notificationSettingsOpen) return ShellTopSurface.NotificationSettings
    if (state.composerOpen) return ShellTopSurface.Composer
    if (state.editProfileOpen) return ShellTopSurface.EditProfile
    if (state.notificationRouteOpen) return ShellTopSurface.NotificationRoute
    if (state.pageOpen) return ShellTopSurface.Page
    return if (state.atHome) null else ShellTopSurface.Home
}
