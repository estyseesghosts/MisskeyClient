package me.foxtails.palustris.ui.session

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Owns the terminal teardown callbacks for one connected entry.
 *
 * The store is activity-scoped, so it survives activity recreation. A composition can leave and
 * return with the same connected lifetime. The store retires a feature model only when the
 * connected lifetime retires, not when a composition is disposed. Terminal shutdown runs on
 * retirement and on [onCleared].
 *
 * A feature model registers its `stop` callback under a stable key. A repeated key replaces the
 * callback, so recreation does not register duplicates.
 */
@HiltViewModel
class ConnectedEntryStore @Inject constructor() : ViewModel() {
    private var generation: Long? = null
    private val teardowns = mutableMapOf<Long, MutableMap<String, () -> Unit>>()

    /** Marks [newGeneration] as the accepted entry and retires every other entry. */
    fun beginEntry(newGeneration: Long) {
        if (generation == newGeneration) return
        generation = newGeneration
        val retired = teardowns.keys.filter { it != newGeneration }
        retired.forEach { entry ->
            teardowns.remove(entry).orEmpty().values.forEach { it() }
        }
    }

    /**
     * Registers the terminal teardown for [entryGeneration].
     *
     * A retired generation runs the callback immediately. A repeated key replaces the value.
     */
    fun register(entryGeneration: Long, key: String, teardown: () -> Unit) {
        val current = generation
        if (current != null && entryGeneration < current) {
            teardown()
            return
        }
        teardowns.getOrPut(entryGeneration) { mutableMapOf() }[key] = teardown
    }

    /** Retires the accepted entry and every retained entry. */
    fun retireAll() {
        val callbacks = teardowns.values.flatMap { it.values }
        teardowns.clear()
        generation = null
        callbacks.forEach { it() }
    }

    override fun onCleared() {
        retireAll()
        super.onCleared()
    }
}
