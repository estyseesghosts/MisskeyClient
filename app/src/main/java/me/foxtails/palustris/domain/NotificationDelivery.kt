package me.foxtails.palustris.domain

enum class NotificationDeliveryState { Pending, Posting, Presented, Suppressed, Failed }

enum class NotificationPushRegistrationState {
    Off,
    NoDistributor,
    DistributorSelectionRequired,
    RegisteringWithDistributor,
    EndpointReceived,
    RegisteringWithServer,
    Connected,
    PermissionRequired,
    TemporarilyUnavailable,
    AccessDenied,
    Removing,
}

data class NotificationDeliveryRecord(
    val accountId: AccountId,
    val notificationId: EntityId,
    val state: NotificationDeliveryState = NotificationDeliveryState.Pending,
    val androidTag: String,
    val androidId: Int,
    val attemptCount: Int = 0,
    val lastAttemptAtEpochMillis: Long = 0,
    val lastErrorCategory: String? = null,
)

data class NotificationSettings(
    val alertsEnabled: Boolean = false,
    val categories: Set<NotificationCategory> = setOf(NotificationCategory.All),
    val showPreviews: Boolean = false,
    val quietHoursStartMinutes: Int? = null,
    val quietHoursEndMinutes: Int? = null,
    val periodicFallbackEnabled: Boolean = false,
    val selectedDistributor: String? = null,
) {
    init {
        require(quietHoursStartMinutes == null || quietHoursStartMinutes in 0..1439)
        require(quietHoursEndMinutes == null || quietHoursEndMinutes in 0..1439)
    }
}

/** Applies a switch's requested state without treating All as a real category. */
fun NotificationSettings.withCategoryEnabled(
    category: NotificationCategory,
    enabled: Boolean,
): NotificationSettings {
    require(category != NotificationCategory.All) { "All is not an individually selectable category." }
    val current = if (NotificationCategory.All in categories) {
        selectableNotificationCategories
    } else {
        categories - NotificationCategory.All
    }
    val next = if (enabled) current + category else current - category
    return copy(categories = next)
}

private val selectableNotificationCategories = NotificationCategory.entries
    .filterNot { it == NotificationCategory.All }
    .toSet()

data class PushRegistration(
    val accountId: AccountId,
    val generation: Long,
    val instanceName: String,
    val distributorPackage: String? = null,
    val endpoint: ValidatedUrl? = null,
    val state: NotificationPushRegistrationState = NotificationPushRegistrationState.Off,
    val endpointGeneration: Long = 0,
    val retryCount: Int = 0,
    val lastErrorCategory: String? = null,
)
