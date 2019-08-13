/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.reverse.adapter

import com.d3.commons.util.createPrettyFixThreadPool
import com.d3.commons.util.hex
import com.d3.reverse.REVERSE_CHAIN_ADAPTER_SERVICE_NAME
import com.d3.reverse.client.isIrohaConnectionError
import com.d3.reverse.config.ReverseChainAdapterConfig
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.map
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.impl.DefaultExceptionHandler
import io.grpc.StatusRuntimeException
import iroha.protocol.TransactionOuterClass
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.Utils
import mu.KLogging
import org.springframework.stereotype.Component
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Reverse chain adapter main logic class
 */
@Component
class ReverseChainAdapter(
    private val reverseChainAdapterConfig: ReverseChainAdapterConfig,
    private val irohaAPI: IrohaAPI
) : Closeable {

    private val connectionFactory = ConnectionFactory()
    private val consumerExecutorService = createPrettyFixThreadPool(REVERSE_CHAIN_ADAPTER_SERVICE_NAME, "rmq-consumer")
    private val started = AtomicBoolean()
    private val connection: Connection
    private val channel: Channel
    private var consumerTag: String? = null

    init {
        // Handle connection errors
        connectionFactory.exceptionHandler = object : DefaultExceptionHandler() {
            override fun handleConnectionRecoveryException(conn: Connection, exception: Throwable) {
                logger.error("RMQ connection error", exception)
                exitProcess(1)
            }

            override fun handleUnexpectedConnectionDriverException(
                conn: Connection,
                exception: Throwable
            ) {
                logger.error("RMQ connection error", exception)
                exitProcess(1)
            }
        }
        connectionFactory.host = reverseChainAdapterConfig.rmqHost
        connectionFactory.port = reverseChainAdapterConfig.rmqPort

        connection = connectionFactory.newConnection(consumerExecutorService)
        channel = connection.createChannel()
        channel.basicQos(16)
        channel.queueDeclare(reverseChainAdapterConfig.transactionQueueName, true, false, false, null)
        logger.info("Queue '${reverseChainAdapterConfig.transactionQueueName}' has been declared")
    }

    /**
     * Initiates the transaction consuming process
     * @return result of operation
     */
    fun init(): Result<Unit, Exception> {
        return Result.of {
            if (!started.compareAndSet(false, true)) {
                throw IllegalStateException("Cannot run reverse chain adapter because it has been started previously")
            }
        }.map {
            val deliverCallback = { _: String, delivery: Delivery ->
                val transaction = TransactionOuterClass.Transaction.parseFrom(delivery.body)
                val txHash = String.hex(Utils.hash(transaction))
                logger.info("New transaction with hash $txHash")
                try {
                    irohaAPI.transactionSync(transaction)
                    channel.basicAck(delivery.envelope.deliveryTag, false)
                    logger.info("Transaction with hash $txHash has been successfully sent to Iroha")
                } catch (e: Exception) {
                    if (e is StatusRuntimeException && isIrohaConnectionError(e)) {
                        logger.warn("Cannot handle transaction with hash $txHash due to Iroha connection error")
                    } else {
                        logger.warn("Cannot handle transaction with hash $txHash due to severe error", e)
                    }
                    channel.basicNack(delivery.envelope.deliveryTag, false, true)
                }
            }
            channel.basicConsume(reverseChainAdapterConfig.transactionQueueName, false, deliverCallback, { _ -> })
            Unit
        }
    }

    override fun close() {
        connection.close()
        consumerTag?.let {
            channel.basicCancel(it)
        }
        consumerExecutorService.shutdownNow()
    }

    companion object : KLogging()
}
