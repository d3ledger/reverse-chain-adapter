package com.d3.reverse.client

/**
 * Reverse chain adapter client library configuration
 */
interface ReverseChainAdapterClientConfig {
    // RMQ hostname
    val rmqHost: String
    // RMQ port
    val rmqPort: Int
    // Name of queue for transactions storage
    val transactionQueueName: String
}
