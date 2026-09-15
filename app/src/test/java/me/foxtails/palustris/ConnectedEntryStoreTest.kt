package me.foxtails.palustris

import androidx.lifecycle.ViewModelStore
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for the connected-entry lifetime.
 *
 * A composition can leave and return with the same connected lifetime. The store must not retire a
 * model during that gap. It retires models only when the connected lifetime changes or the owner
 * clears.
 */
class ConnectedEntryStoreTest {
    @Test
    fun sameLifetimeKeepsRegisteredTeardownAcrossRecomposition() {
        val store = ConnectedEntryStore()
        var retired = 0
        store.beginEntry(1L)
        store.register(1L, "feed") { retired += 1 }

        // A composition leaves and returns with the same lifetime.
        store.beginEntry(1L)

        assertEquals(0, retired)
    }

    @Test
    fun newLifetimeRetiresThePreviousEntry() {
        val store = ConnectedEntryStore()
        var first = 0
        var second = 0
        store.beginEntry(1L)
        store.register(1L, "feed") { first += 1 }

        store.beginEntry(2L)
        store.register(2L, "feed") { second += 1 }
        store.beginEntry(2L)

        assertEquals(1, first)
        assertEquals(0, second)
    }

    @Test
    fun retireAllStopsEveryEntryOnce() {
        val store = ConnectedEntryStore()
        var count = 0
        store.beginEntry(1L)
        store.register(1L, "feed") { count += 1 }
        store.register(1L, "profile") { count += 1 }

        store.retireAll()
        store.retireAll()

        assertEquals(2, count)
    }

    @Test
    fun repeatedKeyReplacesTheTeardown() {
        val store = ConnectedEntryStore()
        var first = 0
        var second = 0
        store.beginEntry(1L)
        store.register(1L, "feed") { first += 1 }
        store.register(1L, "feed") { second += 1 }

        store.retireAll()

        assertEquals(0, first)
        assertEquals(1, second)
    }

    @Test
    fun registrationForARetiredGenerationStopsImmediately() {
        val store = ConnectedEntryStore()
        var retired = 0
        store.beginEntry(2L)

        store.register(1L, "feed") { retired += 1 }

        assertEquals(1, retired)
        store.retireAll()
        assertEquals(1, retired)
    }

    @Test
    fun clearingTheOwnerRetiresTheAcceptedEntry() {
        val store = ConnectedEntryStore()
        var retired = 0
        store.beginEntry(1L)
        store.register(1L, "feed") { retired += 1 }
        val owner = ViewModelStore()
        owner.put("entry", store)

        owner.clear()

        assertEquals(1, retired)
    }
}
