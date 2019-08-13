/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package integration.reverse

import com.d3.commons.sidechain.iroha.util.impl.IrohaQueryHelperImpl
import com.d3.commons.util.getRandomString
import com.d3.reverse.client.ReliableIrohaConsumerImpl
import com.github.kittinunf.result.failure
import integration.reverse.environment.ReverseChainAdapterIntegrationTestEnvironment
import jp.co.soramitsu.iroha.java.Transaction
import mu.KLogging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.*
import java.util.concurrent.atomic.AtomicBoolean

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReverseChainAdapterIntegrationTest {

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
     * @when new transaction is passed to the adapter
     * @then the transaction is committed
     */
    @Test
    fun testSendTransaction() {
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
    }

    /**
     * @given running Iroha, RMQ and reverse chain-adapter
     * @when 10 transaction are passed to the adapter
     * @then all the transactions are committed
     */
    @Test
    fun testSendTransactionsMassive() {
        repeat(10) {
            val reliableIrohaConsumer = ReliableIrohaConsumerImpl(
                environment.createReverseChainAdapterClientConfig(),
                environment.dummyIrohaCredential,
                environment.irohaAPI
            )
            val dummyAccountId = environment.dummyIrohaCredential.accountId
            val key = String.getRandomString(10)
            val value = String.getRandomString(10)
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
        }
    }

    /**
     * @given running Iroha, RMQ and reverse chain-adapter
     * @when 8 threads send 10 transactions to the adapter in parallel (80 tx in total)
     * @then all the transactions are committed
     */
    @Test
    fun testSendTransactionsMassiveParallel() {
        val threads = ArrayList<Thread>()
        val threadsNum = 8
        val successFlag = AtomicBoolean(true)
        val runnable = Runnable {
            repeat(10) {
                val reliableIrohaConsumer = ReliableIrohaConsumerImpl(
                    environment.createReverseChainAdapterClientConfig(),
                    environment.dummyIrohaCredential,
                    environment.irohaAPI
                )
                val dummyAccountId = environment.dummyIrohaCredential.accountId
                val key = String.getRandomString(10)
                val value = String.getRandomString(10)
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
                        if (value != details[key]) {
                            logger.error("Failed assertion. Expected $value, actual ${details[key]}")
                            successFlag.set(false)
                        }
                    }, { ex ->
                        logger.error("Cannot get details", ex)
                        successFlag.set(false)
                    })
                }, { ex ->
                    logger.error("Cannot send transaction", ex)
                    successFlag.set(false)
                })
            }
        }
        repeat(threadsNum)
        {
            threads.add(Thread(runnable))
        }
        threads.forEach {
            it.start()
        }
        threads.forEach {
            it.join()
        }
        assertTrue(successFlag.get())
    }

    companion object : KLogging()
}
