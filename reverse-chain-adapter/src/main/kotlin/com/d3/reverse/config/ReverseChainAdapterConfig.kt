/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.reverse.config

/**
 * Reverse chain adapter configuration class
 */
interface ReverseChainAdapterConfig {
    // RMQ hostname
    val rmqHost: String
    // RMQ port
    val rmqPort: Int
    // Name of queue for transactions storage
    val transactionQueueName: String
    // Iroha hostname
    val irohaHost: String
    // Iroha port
    val irohaPort: Int
    // Health check port
    val healthCheckPort: Int
}
