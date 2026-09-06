package me.foxtails.palustris.domain

interface CapabilityProbe {
    suspend fun probeCapabilities(connection: Connection): ServerCapabilities
}
