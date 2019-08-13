/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package integration.reverse

import com.d3.commons.sidechain.iroha.util.impl.IrohaQueryHelperImpl
import com.d3.reverse.client.ReliableIrohaConsumerImpl
import integration.reverse.environment.ReverseChainAdapterIntegrationTestEnvironment
import jp.co.soramitsu.iroha.java.Transaction
import mu.KLogging
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReverseChainAdapterBeforeStartIntegrationTest {

    private val environment = ReverseChainAdapterIntegrationTestEnvironment()

    @AfterAll
    fun tearDown() {
        environment.close()
    }

    /**
     * @given running Iroha, RMQ
     * @when new transaction is passed to the adapter before its start
     * @then the transaction is committed after the start
     */
    @Test
    fun testSendTransactionBeforeStart() {
        val startAdapterThread = Thread(Runnable {
            // Delay a little bit
            Thread.sleep(5_000)
            environment.adapter.init().fold({
                logger.info("Reverse chain-adapter has been stared")
            }, { ex ->
                logger.error("Cannot init reverse chain-adapter ", ex)
                throw ex
            })
        })
        startAdapterThread.start()
        val reliableIrohaConsumer = ReliableIrohaConsumerImpl(
            environment.createReverseChainAdapterClientConfig(),
            environment.dummyIrohaCredential,
            environment.irohaAPI
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
        startAdapterThread.join()
    }

    companion object : KLogging()
}
