package me.foxtails.palustris.data.notifications.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.keys.DefaultKeyManager

/** Operations owned by the UnifiedPush connector; static Android APIs stay behind this boundary. */
interface UnifiedPushConnector {
    fun availableDistributors(): List<String>

    fun acknowledgedDistributor(): String?

    fun saveDistributor(packageName: String)

    /**
     * Register an instance with the selected distributor.
     *
     * Connector 3.3.5 creates the registration row before asking the key manager to generate
     * the instance keys. Callers must not pre-generate keys for an instance.
     */
    fun register(instanceName: String, messageForDistributor: String?)

    fun unregister(instanceName: String)
}

enum class PushConnectorOperation {
    DistributorDiscovery,
    DistributorSave,
    Registration,
    Unregistration,
}

class PushConnectorFailure(
    val operation: PushConnectorOperation,
    cause: Throwable,
) : IllegalStateException("UnifiedPush ${operation.name.lowercase()} failed", cause)

@Singleton
class AndroidUnifiedPushConnector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UnifiedPushConnector {
    override fun availableDistributors(): List<String> = runConnector(PushConnectorOperation.DistributorDiscovery) {
        UnifiedPush.getDistributors(context)
    }

    override fun acknowledgedDistributor(): String? = runConnector(PushConnectorOperation.DistributorDiscovery) {
        UnifiedPush.getAckDistributor(context)
    }

    override fun saveDistributor(packageName: String) {
        runConnector(PushConnectorOperation.DistributorSave) {
            UnifiedPush.saveDistributor(context, packageName)
        }
    }

    override fun register(instanceName: String, messageForDistributor: String?) {
        runConnector(PushConnectorOperation.Registration) {
            // UnifiedPush.register owns the connector registration-row/key ordering.
            UnifiedPush.register(
                context,
                instanceName,
                messageForDistributor,
                null,
                DefaultKeyManager(context),
            )
        }
    }

    override fun unregister(instanceName: String) {
        runConnector(PushConnectorOperation.Unregistration) {
            UnifiedPush.unregister(context, instanceName, DefaultKeyManager(context))
        }
    }

    private fun <T> runConnector(operation: PushConnectorOperation, block: () -> T): T = try {
        block()
    } catch (error: PushConnectorFailure) {
        throw error
    } catch (error: Exception) {
        throw PushConnectorFailure(operation, error)
    }
}

/** The order is intentionally explicit so it can be tested without static Android APIs. */
internal fun registerWithDistributor(
    connector: UnifiedPushConnector,
    distributorPackage: String,
    instanceName: String,
    messageForDistributor: String,
) {
    connector.saveDistributor(distributorPackage)
    connector.register(instanceName, messageForDistributor)
}
