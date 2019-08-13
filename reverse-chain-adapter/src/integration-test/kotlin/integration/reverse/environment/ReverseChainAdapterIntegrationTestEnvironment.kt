/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package integration.reverse.environment

import com.d3.commons.model.IrohaCredential
import com.d3.commons.util.getRandomString
import com.d3.reverse.adapter.ReverseChainAdapter
import com.d3.reverse.client.ReverseChainAdapterClientConfig
import com.d3.reverse.config.ReverseChainAdapterConfig
import integration.helper.ContainerHelper
import integration.helper.DEFAULT_RMQ_PORT
import iroha.protocol.BlockOuterClass
import iroha.protocol.Primitive
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.Transaction
import jp.co.soramitsu.iroha.testcontainers.IrohaContainer
import jp.co.soramitsu.iroha.testcontainers.PeerConfig
import jp.co.soramitsu.iroha.testcontainers.detail.GenesisBlockBuilder
import java.io.Closeable

const val IROHA_PORT = 50051

/**
 * Reverse chain adapter test environment
 */
class ReverseChainAdapterIntegrationTestEnvironment : Closeable {
    private val queueName = String.getRandomString(6)
    private val peerKeyPair = Ed25519Sha3().generateKeypair()
    private val dummyClientId = "client@d3"
    private val dummyClientKeyPair = Ed25519Sha3().generateKeypair()
    val containerHelper = ContainerHelper()
    val dummyIrohaCredential = IrohaCredential(dummyClientId, dummyClientKeyPair)

    val irohaContainer: IrohaContainer

    val irohaAPI: IrohaAPI

    init {
        containerHelper.rmqContainer.start()
        irohaContainer =
            IrohaContainer.createFixedPortIrohaContainer(IROHA_PORT).withPeerConfig(getPeerConfig()).withLogger(null)
        irohaContainer.start()
        irohaAPI = irohaContainer.api
    }

    private fun createReverseChainAdapterConfig(): ReverseChainAdapterConfig {
        return object : ReverseChainAdapterConfig {
            override val rmqHost = containerHelper.rmqContainer.containerIpAddress
            override val rmqPort = containerHelper.rmqContainer.getMappedPort(DEFAULT_RMQ_PORT)
            override val transactionQueueName = queueName
            override val irohaHost = irohaAPI.uri.host
            override val irohaPort = irohaAPI.uri.port
            override val healthCheckPort = 0
        }
    }

    fun createReverseChainAdapterClientConfig(): ReverseChainAdapterClientConfig {
        return object : ReverseChainAdapterClientConfig {
            override val rmqHost = containerHelper.rmqContainer.containerIpAddress
            override val rmqPort = containerHelper.rmqContainer.getMappedPort(DEFAULT_RMQ_PORT)
            override val transactionQueueName = queueName
        }
    }

    private val adapterDelegate = lazy {
        ReverseChainAdapter(createReverseChainAdapterConfig(), irohaAPI)
    }

    val adapter by adapterDelegate

    /**
     * Returns Iroha peer config
     */
    private fun getPeerConfig(): PeerConfig {
        val config = PeerConfig.builder()
            .genesisBlock(getGenesisBlock())
            .build()
        config.withPeerKeyPair(peerKeyPair)
        return config
    }

    /**
     * Creates test genesis block
     */
    private fun getGenesisBlock(): BlockOuterClass.Block {
        return GenesisBlockBuilder().addTransaction(
            Transaction.builder("")
                .addPeer("0.0.0.0:10001", peerKeyPair.public)
                .createRole("none", emptyList())
                .createRole(
                    "client",
                    listOf(
                        Primitive.RolePermission.can_set_detail,
                        Primitive.RolePermission.can_get_all_acc_detail
                    )
                )
                .createDomain("d3", "client")
                .createAccount(dummyClientId, dummyClientKeyPair.public)
                .build()
                .build()
        ).build()
    }

    override fun close() {
        if (adapterDelegate.isInitialized()) {
            adapter.close()
        }
        irohaAPI.close()
        irohaContainer.close()
        containerHelper.close()
    }
}
