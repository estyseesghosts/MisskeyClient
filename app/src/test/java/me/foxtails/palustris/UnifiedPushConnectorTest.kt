package me.foxtails.palustris

import me.foxtails.palustris.data.notifications.push.UnifiedPushConnector
import me.foxtails.palustris.data.notifications.push.registerWithDistributor
import org.junit.Assert.assertEquals
import org.junit.Test

class UnifiedPushConnectorTest {
    @Test
    fun distributorIsSavedBeforeConnectorRegistrationAndNoAppKeyGenerationOccurs() {
        val connector = RecordingConnector()

        registerWithDistributor(
            connector = connector,
            distributorPackage = "org.unifiedpush.distributor.sunup",
            instanceName = "account-instance",
            messageForDistributor = "MisskeyClient notifications",
        )

        assertEquals(
            listOf(
                "save:org.unifiedpush.distributor.sunup",
                "register:account-instance:null",
            ),
            connector.operations,
        )
    }

    @Test
    fun failedDistributorSaveDoesNotDispatchRegistration() {
        val connector = RecordingConnector(failSave = true)

        runCatching {
            registerWithDistributor(connector, "org.unifiedpush.distributor.sunup", "instance", "message")
        }

        assertEquals(listOf("save:org.unifiedpush.distributor.sunup"), connector.operations)
    }

    private class RecordingConnector(
        private val failSave: Boolean = false,
    ) : UnifiedPushConnector {
        val operations = mutableListOf<String>()

        override fun availableDistributors(): List<String> = emptyList()

        override fun acknowledgedDistributor(): String? = null

        override fun saveDistributor(packageName: String) {
            operations += "save:$packageName"
            if (failSave) error("save failed")
        }

        override fun register(instanceName: String, messageForDistributor: String?, vapidPublicKey: String?) {
            operations += "register:$instanceName:$vapidPublicKey"
        }

        override fun unregister(instanceName: String) {
            operations += "unregister:$instanceName"
        }
    }
}
