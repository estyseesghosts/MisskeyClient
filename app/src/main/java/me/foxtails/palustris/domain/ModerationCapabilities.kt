package me.foxtails.palustris.domain

data class ModerationCapabilities(
    val read: CapabilityStatus = CapabilityStatus.Unknown,
    val write: CapabilityStatus = CapabilityStatus.Unknown,
    val blocked: CapabilityStatus = CapabilityStatus.Unknown,
    val muted: CapabilityStatus = CapabilityStatus.Unknown,
    val hashtags: CapabilityStatus = CapabilityStatus.Unknown,
)
