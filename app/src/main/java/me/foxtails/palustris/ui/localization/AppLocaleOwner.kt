package me.foxtails.palustris.ui.localization

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.ui.localization.AppLocaleController.PlatformReconciliation

/**
 * One owner for locale event direction. Startup reconciliation runs once with first-upgrade
 * precedence. Later decisions follow observed movement: a moved repository exports to the
 * platform, and a moved platform imports into the repository. The latest accepted choice
 * wins in both directions. Every decision runs under one mutex, so concurrent repository
 * emissions and resume checks cannot interleave.
 *
 * The owner records the last accepted repository value, the last observed platform value,
 * and a pending import. A stale platform read after an export therefore re-asserts the
 * repository instead of importing the old platform value back, and a repeated check while
 * an import is in flight repeats that import instead of contradicting it. Canonical tags
 * decide; equal tags never act.
 */
class AppLocaleOwner {
    private val mutex = Mutex()
    private var startupDone = false
    private var lastRepository: AppLanguage? = null
    private var lastObservedPlatformTag: String? = null
    private var pendingImport: AppLanguage? = null

    /**
     * Resolves the current repository and platform values to one platform action. The
     * first call applies first-upgrade precedence through
     * [AppLocaleController.reconcilePlatformSelection]. An in-app selection never
     * imports. An external selection imports, including an external clear, which imports
     * System default.
     */
    suspend fun resolve(
        platformTag: String?,
        repository: AppLanguage,
    ): PlatformReconciliation = mutex.withLock {
        if (!startupDone) {
            startupDone = true
            val action = AppLocaleController.reconcilePlatformSelection(platformTag, repository)
            lastObservedPlatformTag = canonicalTag(platformTag)
            pendingImport = (action as? PlatformReconciliation.ImportToRepository)?.language
            lastRepository = repository
            return@withLock action
        }
        if (pendingImport != null && repository == pendingImport) {
            // A previous import reached the repository.
            pendingImport = null
        }
        if (repository != lastRepository) {
            // The repository moved through an in-app command or an applied import. The
            // repository value is the accepted one, so export it when the platform differs.
            lastRepository = repository
            pendingImport = null
            return@withLock exportWhenDifferent(platformTag, repository)
        }
        if (pendingImport != null && canonicalTag(platformTag) == lastObservedPlatformTag) {
            // The import has not reached the repository and the platform has not moved
            // again. Repeat the import instead of contradicting it.
            return@withLock PlatformReconciliation.ImportToRepository(pendingImport!!)
        }
        if (canonicalTag(platformTag) != lastObservedPlatformTag) {
            // The repository is unchanged and the platform moved. The external choice
            // wins, including an external clear, which imports System default.
            lastObservedPlatformTag = canonicalTag(platformTag)
            return@withLock importWhenDifferent(platformTag, repository)
        }
        // Neither side moved, yet the values differ. A recent export has not converged
        // in the platform read. Re-assert the accepted repository value.
        return@withLock exportWhenDifferent(platformTag, repository)
    }

    private fun exportWhenDifferent(
        platformTag: String?,
        repository: AppLanguage,
    ): PlatformReconciliation =
        if (canonicalTag(platformTag) == repository.tag) {
            PlatformReconciliation.NoOp
        } else {
            PlatformReconciliation.ExportToPlatform(repository)
        }

    private fun importWhenDifferent(
        platformTag: String?,
        repository: AppLanguage,
    ): PlatformReconciliation {
        val platform = AppLocaleController.languageForTag(platformTag)
        return if (platform.tag == repository.tag) {
            pendingImport = null
            PlatformReconciliation.NoOp
        } else {
            pendingImport = platform
            PlatformReconciliation.ImportToRepository(platform)
        }
    }

    private fun canonicalTag(platformTag: String?): String? =
        if (platformTag.isNullOrBlank()) null
        else AppLocaleController.languageForTag(platformTag).tag
}
