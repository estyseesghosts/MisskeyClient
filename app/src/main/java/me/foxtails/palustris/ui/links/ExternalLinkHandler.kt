package me.foxtails.palustris.ui.links

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.TrackingParameterCleaner

object ExternalLinkHandler {
    @Volatile
    var cleanTrackingParameters: Boolean = false

    fun prepare(url: String): String = TrackingParameterCleaner.clean(url, cleanTrackingParameters)

    /**
     * Opens one prepared web URL in an external browser. A null, blank, or non-HTTP(S) URL
     * without a host is a no-op. The Intent is created at most once, and a missing browser
     * shows the standard error Toast.
     */
    fun open(context: Context, url: String?) {
        val uri = url?.let(::prepare)?.toUri() ?: return
        if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.error_no_app_open_link), Toast.LENGTH_SHORT).show()
        }
    }
}
