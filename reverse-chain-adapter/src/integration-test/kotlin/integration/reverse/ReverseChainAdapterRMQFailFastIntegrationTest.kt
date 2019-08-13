package integration.reverse

import integration.helper.DEFAULT_RMQ_PORT
import integration.reverse.environment.ReverseChainAdapterIntegrationTestEnvironment
import org.junit.Assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReverseChainAdapterRMQFailFastIntegrationTest {

    private val environment = ReverseChainAdapterIntegrationTestEnvironment()

    private val userDir = System.getProperty("user.dir")!!
    private val dockerfile = "$userDir/build/docker/Dockerfile"
    private val reverseChainAdapterContextFolder = "$userDir/build/docker/"

    private val reverseChainAdapterContainer =
        environment.containerHelper.createSoraPluginContainer(reverseChainAdapterContextFolder, dockerfile)

    @BeforeAll
    fun setUp() {
        // Set RMQ host
        reverseChainAdapterContainer.addEnv("REVERSE-CHAIN-ADAPTER_RMQHOST", "localhost")
        reverseChainAdapterContainer.addEnv(
            "REVERSE-CHAIN-ADAPTER_RMQPORT",
            environment.containerHelper.rmqContainer.getMappedPort(DEFAULT_RMQ_PORT).toString()
        )
        // Set Iroha host and port
        reverseChainAdapterContainer.addEnv("REVERSE-CHAIN-ADAPTER_IROHA_HOSTNAME", "localhost")
        reverseChainAdapterContainer.addEnv(
            "REVERSE-CHAIN-ADAPTER_IROHA_PORT",
            environment.irohaContainer.toriiAddress.port.toString()
        )
        reverseChainAdapterContainer.start()
    }

    @AfterAll
    fun tearDown() {
        reverseChainAdapterContainer.stop()
        environment.close()
    }

    /**
     * @given reverse chain adapter and RMQ services being started
     * @when RMQ dies
     * @then reverse chain adapter dies as well
     */
    @Test
    fun testFailFast() {
        // Let the service work a little
        Thread.sleep(15_000)
        Assert.assertTrue(environment.containerHelper.isServiceHealthy(reverseChainAdapterContainer))
        // Kill RMQ
        environment.containerHelper.rmqContainer.stop()
        // Wait a little
        Thread.sleep(15_000)
        // Check that the service is dead
        Assert.assertTrue(environment.containerHelper.isServiceDead(reverseChainAdapterContainer))
    }
}
