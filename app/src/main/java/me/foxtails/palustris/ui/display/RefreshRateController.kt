package me.foxtails.palustris.ui.display

import android.os.Build
import android.view.Window

/** Applies a best-effort same-resolution 60 Hz window preference and restores the default mode. */
class RefreshRateController(private val window: Window) {
    private var originalModeId: Int? = null

    fun apply(request60Hz: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val display = window.windowManager.defaultDisplay
        val current = display.mode
        if (originalModeId == null) originalModeId = window.attributes.preferredDisplayModeId
        val mode = if (request60Hz) {
            display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }
        } else null
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = mode?.modeId ?: originalModeId ?: 0
        }
    }

    fun restore() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        window.attributes = window.attributes.apply { preferredDisplayModeId = originalModeId ?: 0 }
    }
}
