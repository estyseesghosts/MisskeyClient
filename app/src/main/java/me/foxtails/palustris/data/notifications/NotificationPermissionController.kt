package me.foxtails.palustris.data.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationPermissionController {
    fun isGranted(): Boolean
}

@Singleton
class AndroidNotificationPermissionController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NotificationPermissionController {
    override fun isGranted(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
