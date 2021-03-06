/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.reverse.config

import com.d3.commons.config.loadRawLocalConfigs
import com.d3.commons.healthcheck.HealthCheckEndpoint
import jp.co.soramitsu.iroha.java.IrohaAPI
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

val reverseChainAdapterConfig =
    loadRawLocalConfigs(
        "reverse-chain-adapter",
        ReverseChainAdapterConfig::class.java,
        "reverse-chain-adapter.properties"
    )

@Configuration
class ReverseChainAdapterAppConfiguration {

    @Bean
    fun reverseChainAdapterConfig() = reverseChainAdapterConfig

    @Bean
    fun irohaAPI() = IrohaAPI(reverseChainAdapterConfig.irohaHost, reverseChainAdapterConfig.irohaPort)

    @Bean
    fun healthCheckEndpoint() = HealthCheckEndpoint(reverseChainAdapterConfig.healthCheckPort)
}
