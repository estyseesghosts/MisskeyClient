package me.foxtails.palustris.domain

data class Event(val type: String, val post: Post? = null, val notification: Notification? = null)
