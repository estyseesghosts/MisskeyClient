package me.foxtails.palustris

import me.foxtails.palustris.ui.navigation.ShellBackState
import me.foxtails.palustris.ui.navigation.ShellTopSurface
import me.foxtails.palustris.ui.navigation.topSurfaceForBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellBackPolicyTest {
    @Test
    fun idleHomeHasNoBackTarget() {
        assertNull(topSurfaceForBack(ShellBackState()))
        assertNull(topSurfaceForBack(ShellBackState(atHome = true, pageOpen = false)))
    }

    @Test
    fun mediaViewerOwnsBackWhileOpen() {
        val state = ShellBackState(
            mediaViewerOpen = true,
            profileImageOpen = true,
            singlePostOpen = true,
            pageOpen = true,
            atHome = false,
        )

        assertNull(topSurfaceForBack(state))
    }

    @Test
    fun profileImageDismissesBeforeEveryOtherSurface() {
        val state = ShellBackState(
            profileImageOpen = true,
            composerOpen = true,
            singlePostOpen = true,
            pageOpen = true,
            atHome = false,
        )

        assertEquals(ShellTopSurface.ProfileImage, topSurfaceForBack(state))
    }

    @Test
    fun wideLayoutDismissesItsOverlayBeforeTheSelectedPost() {
        assertEquals(
            ShellTopSurface.NotificationSettings,
            topSurfaceForBack(
                ShellBackState(
                    largePresentation = true,
                    notificationSettingsOpen = true,
                    singlePostOpen = true,
                    atHome = false,
                ),
            ),
        )
        assertEquals(
            ShellTopSurface.Composer,
            topSurfaceForBack(
                ShellBackState(
                    largePresentation = true,
                    composerOpen = true,
                    singlePostOpen = true,
                    atHome = false,
                ),
            ),
        )
        assertEquals(
            ShellTopSurface.EditProfile,
            topSurfaceForBack(
                ShellBackState(
                    largePresentation = true,
                    editProfileOpen = true,
                    singlePostOpen = true,
                    atHome = false,
                ),
            ),
        )
    }

    @Test
    fun selectedPostDismissesBeforeOverlaysInCompactLayout() {
        assertEquals(
            ShellTopSurface.SinglePost,
            topSurfaceForBack(
                ShellBackState(
                    singlePostOpen = true,
                    composerOpen = true,
                    atHome = false,
                ),
            ),
        )
    }

    @Test
    fun overlaysDismissInSettingsComposerEditorOrder() {
        assertEquals(
            ShellTopSurface.NotificationSettings,
            topSurfaceForBack(
                ShellBackState(
                    notificationSettingsOpen = true,
                    composerOpen = true,
                    editProfileOpen = true,
                    atHome = false,
                ),
            ),
        )
        assertEquals(
            ShellTopSurface.Composer,
            topSurfaceForBack(
                ShellBackState(
                    composerOpen = true,
                    editProfileOpen = true,
                    atHome = false,
                ),
            ),
        )
        assertEquals(
            ShellTopSurface.EditProfile,
            topSurfaceForBack(ShellBackState(editProfileOpen = true, atHome = false)),
        )
    }

    @Test
    fun notificationRouteDismissesBeforeTheLocalPage() {
        assertEquals(
            ShellTopSurface.NotificationRoute,
            topSurfaceForBack(
                ShellBackState(
                    notificationRouteOpen = true,
                    pageOpen = true,
                    atHome = false,
                ),
            ),
        )
        assertEquals(
            ShellTopSurface.Page,
            topSurfaceForBack(ShellBackState(pageOpen = true, atHome = false)),
        )
    }

    @Test
    fun anOpenDestinationWithoutSurfacesReturnsHome() {
        assertEquals(ShellTopSurface.Home, topSurfaceForBack(ShellBackState(atHome = false)))
    }
}
