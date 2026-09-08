package me.foxtails.palustris.ui

/** Compatibility aliases while UI consumers migrate to the application data owner. */
typealias AccountSyncState = me.foxtails.palustris.data.notifications.NotificationSyncState
typealias AccountNotificationSyncController = me.foxtails.palustris.data.notifications.NotificationSyncController
typealias NoOpAccountNotificationSyncController = me.foxtails.palustris.data.notifications.NoOpNotificationSyncController
typealias AccountSyncCoordinator = me.foxtails.palustris.data.notifications.NotificationSyncOrchestrator
