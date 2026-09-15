package me.foxtails.palustris

import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.ui.localization.AppLocaleController.PlatformReconciliation.ExportToPlatform
import me.foxtails.palustris.ui.localization.AppLocaleController.PlatformReconciliation.ImportToRepository
import me.foxtails.palustris.ui.localization.AppLocaleController.PlatformReconciliation.NoOp
import me.foxtails.palustris.ui.localization.AppLocaleOwner
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLocaleOwnerTest {
    @Test
    fun firstUpgradeImportsAnExplicitPlatformLocale() = runTest {
        val owner = AppLocaleOwner()

        assertEquals(
            ImportToRepository(AppLanguage.German),
            owner.resolve("de", AppLanguage.SystemDefault),
        )
    }

    @Test
    fun startupExportsAStoredPreferenceWhenThePlatformIsEmpty() = runTest {
        val owner = AppLocaleOwner()

        assertEquals(
            ExportToPlatform(AppLanguage.French),
            owner.resolve(null, AppLanguage.French),
        )
    }

    @Test
    fun appliedStartupImportConvergesQuietly() = runTest {
        val owner = AppLocaleOwner()
        assertEquals(
            ImportToRepository(AppLanguage.German),
            owner.resolve("de", AppLanguage.SystemDefault),
        )

        // The repository applies the import. The next decision stays quiet.
        assertEquals(
            NoOp,
            owner.resolve("de", AppLanguage.German),
        )
    }

    @Test
    fun repeatedCheckBeforeAnAppliedImportRepeatsTheImport() = runTest {
        val owner = AppLocaleOwner()
        assertEquals(
            ImportToRepository(AppLanguage.German),
            owner.resolve("de", AppLanguage.SystemDefault),
        )

        // The repository has not applied the import and the platform has not moved.
        // The owner repeats the import instead of exporting the stale repository value.
        assertEquals(
            ImportToRepository(AppLanguage.German),
            owner.resolve("de", AppLanguage.SystemDefault),
        )
    }

    @Test
    fun inAppSelectionExportsOverADifferingPlatform() = runTest {
        val owner = AppLocaleOwner()
        assertEquals(
            NoOp,
            owner.resolve("fr", AppLanguage.French),
        )

        // The user selects German in Beeline while the platform still reads French.
        // The old reconciliation imported French back. The owner must export German.
        assertEquals(
            ExportToPlatform(AppLanguage.German),
            owner.resolve("fr", AppLanguage.German),
        )

        // The platform converges on German. Later checks stay quiet.
        assertEquals(
            NoOp,
            owner.resolve("de", AppLanguage.German),
        )
        assertEquals(
            NoOp,
            owner.resolve("de", AppLanguage.German),
        )
    }

    @Test
    fun inAppSelectionToSystemDefaultClearsThePlatform() = runTest {
        val owner = AppLocaleOwner()
        assertEquals(
            NoOp,
            owner.resolve("fr", AppLanguage.French),
        )

        assertEquals(
            ExportToPlatform(AppLanguage.SystemDefault),
            owner.resolve("fr", AppLanguage.SystemDefault),
        )
        assertEquals(
            NoOp,
            owner.resolve(null, AppLanguage.SystemDefault),
        )
    }

    @Test
    fun externalSelectionAfterConvergenceImports() = runTest {
        val owner = AppLocaleOwner()
        assertEquals(
            NoOp,
            owner.resolve("fr", AppLanguage.French),
        )

        // The user picks Japanese in the Android settings. The repository is unchanged,
        // so the external choice wins.
        assertEquals(
            ImportToRepository(AppLanguage.Japanese),
            owner.resolve("ja", AppLanguage.French),
        )

        // The repository converges on Japanese. The next decision stays quiet.
        assertEquals(
            NoOp,
            owner.resolve("ja", AppLanguage.Japanese),
        )
    }

    @Test
    fun externalClearingImportsSystemDefault() = runTest {
        val owner = AppLocaleOwner()
        assertEquals(
            NoOp,
            owner.resolve("fr", AppLanguage.French),
        )

        // Clearing the language through Android must not repopulate the platform
        // with the stored explicit language.
        assertEquals(
            ImportToRepository(AppLanguage.SystemDefault),
            owner.resolve(null, AppLanguage.French),
        )
    }

    @Test
    fun repeatedCheckBeforeAnAppliedExternalImportRepeatsTheImport() = runTest {
        val owner = AppLocaleOwner()
        assertEquals(
            NoOp,
            owner.resolve("fr", AppLanguage.French),
        )
        assertEquals(
            ImportToRepository(AppLanguage.SystemDefault),
            owner.resolve(null, AppLanguage.French),
        )

        // The repository has not applied the import and the platform has not moved
        // again. The owner repeats the import instead of exporting the cleared value.
        assertEquals(
            ImportToRepository(AppLanguage.SystemDefault),
            owner.resolve("", AppLanguage.French),
        )

        // The repository applies the import. The next decision stays quiet.
        assertEquals(
            NoOp,
            owner.resolve(null, AppLanguage.SystemDefault),
        )
    }

    @Test
    fun stalePlatformReadAfterExportDoesNotUndoTheUserChoice() = runTest {
        val owner = AppLocaleOwner()
        assertEquals(
            NoOp,
            owner.resolve("de", AppLanguage.German),
        )
        assertEquals(
            ExportToPlatform(AppLanguage.French),
            owner.resolve("de", AppLanguage.French),
        )

        // The platform read still shows German before the export converges.
        // The owner re-asserts French instead of importing German back.
        assertEquals(
            ExportToPlatform(AppLanguage.French),
            owner.resolve("de", AppLanguage.French),
        )
    }

    @Test
    fun repositoryMoveDuringExternalCheckKeepsTheUserChoice() = runTest {
        val owner = AppLocaleOwner()
        assertEquals(
            NoOp,
            owner.resolve("fr", AppLanguage.French),
        )

        // The repository already moved to Japanese, but the resume check still reads
        // the old platform value. The newer in-app choice wins.
        assertEquals(
            ExportToPlatform(AppLanguage.Japanese),
            owner.resolve("fr", AppLanguage.Japanese),
        )
    }

    @Test
    fun newerExternalSelectionSupersedesAPendingImport() = runTest {
        val owner = AppLocaleOwner()
        assertEquals(
            NoOp,
            owner.resolve("fr", AppLanguage.French),
        )
        assertEquals(
            ImportToRepository(AppLanguage.Japanese),
            owner.resolve("ja", AppLanguage.French),
        )

        // Before the Japanese import lands, the user picks German externally.
        // The newest external choice wins.
        assertEquals(
            ImportToRepository(AppLanguage.German),
            owner.resolve("de", AppLanguage.French),
        )
    }

    @Test
    fun deviceLanguageChangeUnderSystemDefaultStaysQuiet() = runTest {
        val owner = AppLocaleOwner()
        assertEquals(
            NoOp,
            owner.resolve(null, AppLanguage.SystemDefault),
        )

        assertEquals(
            NoOp,
            owner.resolve(null, AppLanguage.SystemDefault),
        )
    }
}
