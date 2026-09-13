package me.foxtails.palustris.data.notifications

import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationReadState
import me.foxtails.palustris.domain.NotificationReadStatus

internal fun Notification.mergeReadState(previous: NotificationReadState?): Notification {
    val known = readState.status.takeUnless { it == NotificationReadStatus.Unknown }
        ?: previous?.status ?: NotificationReadStatus.Unknown
    return copy(readState = readState.copy(
        status = known,
        locallySeen = readState.locallySeen || previous?.locallySeen == true,
        serverAcknowledged = readState.serverAcknowledged || previous?.serverAcknowledged == true,
        androidPresented = readState.androidPresented || previous?.androidPresented == true,
        androidDismissed = readState.androidDismissed || previous?.androidDismissed == true,
    ))
}
