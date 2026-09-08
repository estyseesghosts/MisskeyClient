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

enum class PushRegistrationFailureStage {
    DistributorSelection,
    DistributorSave,
    ConnectorRegistration,
    EndpointValidation,
    ServerSubscription,
    Callback,
    Removal,
}

enum class PushRegistrationFailureReason {
    ConnectorFailure,
    NoDistributor,
    InvalidEndpoint,
    MissingKeys,
    Unauthorized,
    Network,
    RateLimited,
    Unsupported,
    Server,
    StaleCallback,
    Unknown,
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
    val claimId: String? = null,
    val claimExpiresAtEpochMillis: Long = 0,
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
    val sessionRevision: Long = 1L,
    val instanceName: String,
    val distributorPackage: String? = null,
    /** The latest endpoint reported by the distributor; it may still be pending server registration. */
    val endpoint: ValidatedUrl? = null,
    /** The last endpoint accepted by the server; a newer distributor endpoint may still be pending. */
    val serverEndpoint: ValidatedUrl? = null,
    val serverRemoteId: String? = null,
    val confirmedEndpointGeneration: Long = 0,
    val state: NotificationPushRegistrationState = NotificationPushRegistrationState.Off,
    val endpointGeneration: Long = 0,
    val retryCount: Int = 0,
    val lastErrorCategory: String? = null,
    val lastErrorDetail: String? = null,
    val failureStage: PushRegistrationFailureStage? = null,
    val failureReason: PushRegistrationFailureReason? = null,
    val nextRetryAtEpochMillis: Long = 0,
)
