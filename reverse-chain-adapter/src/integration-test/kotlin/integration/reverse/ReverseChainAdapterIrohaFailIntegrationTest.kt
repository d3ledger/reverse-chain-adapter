/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package integration.reverse

import com.d3.commons.sidechain.iroha.util.impl.IrohaQueryHelperImpl
import com.d3.reverse.client.ReliableIrohaConsumerImpl
import com.github.kittinunf.result.failure
import integration.reverse.environment.ReverseChainAdapterIntegrationTestEnvironment
import jp.co.soramitsu.iroha.java.Transaction
import mu.KLogging
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReverseChainAdapterIrohaFailIntegrationTest {

    private val environment = ReverseChainAdapterIntegrationTestEnvironment()

    @BeforeAll
    fun setUp() {
        environment.adapter.init().failure { ex -> throw ex }
    }

    @AfterAll
    fun tearDown() {
        environment.close()
    }

    /**
     * @given running Iroha, RMQ and reverse chain-adapter
     * @when transaction is sent to the adapter after Iroha failure
     * @then transaction is committed after Iroha restart
     */
    @Test
    fun testSendTransactionOnIrohaFailure() {
        environment.irohaContainer.postgresDockerContainer.stop()
        environment.irohaContainer.irohaDockerContainer.stop()
        val startIrohaThread = Thread(Runnable {
            try {
                // Delay a little bit
                Thread.sleep(5_000)
                environment.irohaContainer.start()
            } catch (e: Exception) {
                logger.error("Cannot start Iroha")
            }
        })
        startIrohaThread.start()
        val reliableIrohaConsumer = ReliableIrohaConsumerImpl(
            environment.createReverseChainAdapterClientConfig(),
            environment.dummyIrohaCredential,
            environment.irohaAPI,
            quorum = 1
        )
        val dummyAccountId = environment.dummyIrohaCredential.accountId
        val key = "key"
        val value = "value"
        val transaction = Transaction.builder(
            dummyAccountId
        ).setAccountDetail(
            dummyAccountId,
            key,
            value
        ).sign(environment.dummyIrohaCredential.keyPair).build()
        reliableIrohaConsumer.send(transaction).fold({
            val queryHelper = IrohaQueryHelperImpl(environment.irohaAPI, environment.dummyIrohaCredential)
            queryHelper.getAccountDetails(
                dummyAccountId,
                dummyAccountId
            ).fold({ details ->
                assertEquals(value, details[key])
            }, { ex ->
                fail(ex)
            })
        }, { ex ->
            fail(ex)
        })
        startIrohaThread.join()
    }

    companion object : KLogging()
}
