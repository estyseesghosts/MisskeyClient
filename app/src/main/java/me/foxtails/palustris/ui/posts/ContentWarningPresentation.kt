package me.foxtails.palustris.ui

import androidx.compose.runtime.staticCompositionLocalOf
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.HiddenContentPresentation

/** One presentation policy is shared by feed, search, profiles, notifications, and viewers. */
val LocalContentWarningRules = staticCompositionLocalOf { ContentWarningRules() }
val LocalHiddenContentPresentation = staticCompositionLocalOf { HiddenContentPresentation.Placeholder }
val LocalMutedHashtags = staticCompositionLocalOf<Set<String>> { emptySet() }
