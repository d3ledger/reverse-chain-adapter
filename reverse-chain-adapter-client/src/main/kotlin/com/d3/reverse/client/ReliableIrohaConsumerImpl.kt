package com.d3.reverse.client

import com.d3.commons.model.IrohaCredential
import com.d3.commons.sidechain.iroha.consumer.IrohaConsumer
import com.d3.commons.sidechain.iroha.consumer.status.IrohaTxStatus
import com.d3.commons.sidechain.iroha.consumer.status.createTxStatusObserver
import com.d3.commons.sidechain.iroha.consumer.terminalStatuses
import com.d3.commons.sidechain.iroha.util.impl.IrohaQueryHelperImpl
import com.d3.commons.util.hex
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.map
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.MessageProperties
import io.grpc.Status
import io.grpc.StatusRuntimeException
import iroha.protocol.TransactionOuterClass
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.Transaction
import jp.co.soramitsu.iroha.java.Utils
import jp.co.soramitsu.iroha.java.detail.BuildableAndSignable
import jp.co.soramitsu.iroha.java.subscription.WaitForTerminalStatus
import mu.KLogging
import java.util.concurrent.atomic.AtomicReference

/**
 * Iroha consumer that uses RabbitMQ as a proxy
 * @param reverseChainAdapterClientConfig - Reverse chain adapter client config object
 * @param irohaCredential - Iroha account credential that will be used to sign transactions
 * @param irohaAPI - Iroha network layer. Used to get transactions statuses
 * @param fireAndForget - the consumer won't wait any transaction status if this flag is on. This flag is false by default
 * @param quorum - quorum of [irohaCredential]. This value is taken from Iroha by default. May be stale.
 */
class ReliableIrohaConsumerImpl(
    private val reverseChainAdapterClientConfig: ReverseChainAdapterClientConfig,
    private val irohaCredential: IrohaCredential,
    private val irohaAPI: IrohaAPI,
    private val fireAndForget: Boolean = false,
    var quorum: Int = IrohaQueryHelperImpl(
        irohaAPI,
        irohaCredential.accountId,
        irohaCredential.keyPair
    ).getAccountQuorum(irohaCredential.accountId).get()
) : IrohaConsumer {

    private val connectionFactory = ConnectionFactory()
    private val waitForTerminalStatus = WaitForTerminalStatus(terminalStatuses)

    init {
        connectionFactory.host = reverseChainAdapterClientConfig.rmqHost
        connectionFactory.port = reverseChainAdapterClientConfig.rmqPort

        // Create a queue
        connectionFactory.newConnection().use { connection ->
            connection.createChannel().use { channel ->
                channel.queueDeclare(reverseChainAdapterClientConfig.transactionQueueName, true, false, false, null)
            }
        }
        /*
        Quorum value. This value is set on init() and not updated.
        We must take into account that this value may be stale
         */

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
                                "(queue '${reverseChainAdapterClientConfig.transactionQueueName}')"
                    )
                }
            }
        }.map {
            if (fireAndForget) {
                return@map String.hex(Utils.hash(tx))
            }
            var subscriptionAttempt = 0
            // Wait a transaction until it is received
            while (!Thread.currentThread().isInterrupted) {
                val statusReference = AtomicReference<IrohaTxStatus>()
                try {
                    waitForTerminalStatus.subscribe(irohaAPI, Utils.hash(tx))
                        .blockingSubscribe(createTxStatusObserver(statusReference).build())
                    if (statusReference.get().isSuccessful()) {
                        return@map String.hex(Utils.hash(tx))
                    } else {
                        throw statusReference.get().txException!!
                    }
                } catch (e: IllegalStateException) {
                    if (!isIrohaConnectionError(e)) {
                        throw e
                    } else {
                        waitReconnect()
                        subscriptionAttempt++
                        logger.warn(
                            "Failed to subscribe to tx ${String.hex(Utils.hash(tx))} status due to GRPC error. " +
                                    "Try again(attempt $subscriptionAttempt)."
                        )
                    }
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
    override fun getConsumerQuorum() = Result.of { quorum }

    override fun sign(utx: Transaction): Result<BuildableAndSignable<TransactionOuterClass.Transaction>, Exception> {
        return Result.of { utx.sign(irohaCredential.keyPair) }
    }

    /** {@inheritDoc} */
    override fun send(lst: Iterable<TransactionOuterClass.Transaction>): Result<List<String>, Exception> {
        throw UnsupportedOperationException()
    }

    /** {@inheritDoc} */
    override fun send(lst: List<Transaction>): Result<Map<String, Boolean>, Exception> {
        throw UnsupportedOperationException()
    }

    /**
     * Waits a little for a Iroha reconnection
     */
    private fun waitReconnect() {
        Thread.sleep(5_000)
    }

    companion object : KLogging()
}

/**
 * Checks that [error] is an IO error
 * @param error - error wrapped in a IllegalStateException object
 * @return true if [error] is a Iroha connection error
 */
fun isIrohaConnectionError(error: IllegalStateException): Boolean {
    val cause = error.cause
    return cause != null && cause is StatusRuntimeException && isIrohaConnectionError(cause)
}

/**
 * Checks that [error] is an IO error
 * @param error - GRPC exception
 * @return true if [error] is a Iroha connection error
 */
fun isIrohaConnectionError(error: StatusRuntimeException) = error.status.code == Status.UNAVAILABLE.code

