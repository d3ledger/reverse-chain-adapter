package com.d3.reverse.client

import com.d3.commons.model.IrohaCredential
import com.d3.commons.sidechain.iroha.consumer.IrohaConsumerImpl
import com.d3.commons.sidechain.iroha.consumer.status.TxState
import com.d3.commons.sidechain.iroha.consumer.status.TxStatus
import com.d3.commons.util.hex
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.map
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.MessageProperties
import iroha.protocol.TransactionOuterClass
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.Transaction
import jp.co.soramitsu.iroha.java.Utils
import java.util.concurrent.atomic.AtomicReference

/**
 * Iroha consumer that uses RabbitMQ as a proxy
 */
class ReliableIrohaConsumerImpl(
    private val reverseChainAdapterClientConfig: ReverseChainAdapterClientConfig,
    irohaCredential: IrohaCredential,
    private val irohaAPI: IrohaAPI
) : IrohaConsumerImpl(irohaCredential, irohaAPI) {

    private val connectionFactory = ConnectionFactory()

    init {
        connectionFactory.host = reverseChainAdapterClientConfig.rmqHost
        connectionFactory.port = reverseChainAdapterClientConfig.rmqPort

        // Create queue
        connectionFactory.newConnection().use { connection ->
            connection.createChannel().use { channel ->
                channel.queueDeclare(reverseChainAdapterClientConfig.transactionQueueName, true, false, false, null)
            }
        }
    }

    override val creator = irohaCredential.accountId

    /** {@inheritDoc} */
    override fun send(tx: TransactionOuterClass.Transaction): Result<String, Exception> {
        return Result.of {
            // Send transaction to RabbitMQ
            connectionFactory.newConnection().use { connection ->
                connection.createChannel().use { channel ->
                    channel.basicPublish(
                        "",
                        reverseChainAdapterClientConfig.transactionQueueName,
                        MessageProperties.MINIMAL_PERSISTENT_BASIC,
                        tx.toByteArray()
                    )
                    logger.info(
                        "Tx with hash ${String.hex(Utils.hash(tx))} has been sent to the reverse chain-adapter" +
                                "(queue ${reverseChainAdapterClientConfig.transactionQueueName})"
                    )
                }
            }
        }.map {
            // Wait a transaction until it is received
            while (!Thread.currentThread().isInterrupted) {
                val statusReference = AtomicReference<TxStatus>()
                waitForTerminalStatus.subscribe(irohaAPI, Utils.hash(tx))
                    .blockingSubscribe(getTxStatusObserver(statusReference).build())
                if (statusReference.get().state == TxState.NOT_RECEIVED) {
                    continue
                } else if (statusReference.get().isSuccessful()) {
                    return@map String.hex(Utils.hash(tx))
                } else {
                    throw statusReference.get().txException!!
                }
            }
            throw IllegalStateException("Cannot send transaction. Thread was stopped.")
        }
    }

    /** {@inheritDoc} */
    override fun send(utx: Transaction): Result<String, Exception> {
        return sign(utx).flatMap { transaction ->
            send(transaction.build())
        }
    }

    /** {@inheritDoc} */
    override fun send(lst: Iterable<TransactionOuterClass.Transaction>): Result<List<String>, Exception> {
        throw UnsupportedOperationException()
    }

    /** {@inheritDoc} */
    override fun send(lst: List<Transaction>): Result<Map<String, Boolean>, Exception> {
        throw UnsupportedOperationException()
    }
}