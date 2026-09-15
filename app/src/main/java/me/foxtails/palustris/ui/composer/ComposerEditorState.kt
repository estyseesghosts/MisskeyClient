package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import me.foxtails.palustris.domain.Audience

/**
 * Saveable composer editor fields for one connected account.
 *
 * The composer owner holds these values. The navigation shell reads them but does not own them. The
 * saved snapshot drives the dirty check. Entity targets and the draft list are not stored here
 * because the current product behavior does not restore them.
 */
data class ComposerEditorState(
    val draftId: String? = null,
    val text: String = "",
    val savedText: String = "",
    val warning: String = "",
    val savedWarning: String = "",
    val warningEnabled: Boolean = false,
    val audience: Audience = Audience.Public,
    val savedAudience: Audience = Audience.Public,
    val error: String? = null,
) {
    companion object {
        /** Restores shell-surviving fields across process recreation. */
        val Saver: Saver<ComposerEditorState, Any> = listSaver(
            save = {
                listOf(
                    it.draftId,
                    it.text,
                    it.savedText,
                    it.warning,
                    it.savedWarning,
                    it.warningEnabled,
                    it.audience.name,
                    it.savedAudience.name,
                    it.error,
                )
            },
            restore = {
                ComposerEditorState(
                    draftId = it[0] as String?,
                    text = it[1] as String,
                    savedText = it[2] as String,
                    warning = it[3] as String,
                    savedWarning = it[4] as String,
                    warningEnabled = it[5] as Boolean,
                    audience = Audience.valueOf(it[6] as String),
                    savedAudience = Audience.valueOf(it[7] as String),
                    error = it[8] as String?,
                )
            },
        )
    }
}
