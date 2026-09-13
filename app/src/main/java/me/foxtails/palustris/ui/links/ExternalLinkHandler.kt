package me.foxtails.palustris.ui.links

import android.content.Context
import me.foxtails.palustris.domain.TrackingParameterCleaner

object ExternalLinkHandler {
    @Volatile
    var cleanTrackingParameters: Boolean = false

    fun prepare(url: String): String = TrackingParameterCleaner.clean(url, cleanTrackingParameters)

    fun open(context: Context, url: String?) = me.foxtails.palustris.ui.openExternal(
        context,
        url?.let(::prepare),
    )
}
