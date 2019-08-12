/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.reverse.adapter

import com.d3.commons.config.loadRawLocalConfigs
import com.d3.commons.healthcheck.HealthCheckEndpoint
import com.d3.reverse.config.ReverseChainAdapterConfig
import jp.co.soramitsu.iroha.java.IrohaAPI
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

val reverseChainAdapter =
    loadRawLocalConfigs(
        "reverse-chain-adapter",
        ReverseChainAdapterConfig::class.java,
        "reverse-chain-adapter.properties"
    )

@Configuration
class ReverseChainAdapterAppConfiguration {

    @Bean
    fun reverseChainAdapter() = reverseChainAdapter

    @Bean
    fun irohaAPI() = IrohaAPI(reverseChainAdapter.irohaHost, reverseChainAdapter.irohaPort)

    @Bean
    fun healthCheckEndpoint() = HealthCheckEndpoint(reverseChainAdapter.healthCheckPort)
}
