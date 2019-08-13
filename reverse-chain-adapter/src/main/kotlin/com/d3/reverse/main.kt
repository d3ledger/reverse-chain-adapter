/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

@file:JvmName("ReverseChainAdapterMain")

package com.d3.reverse

import com.d3.reverse.adapter.ReverseChainAdapter
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.failure
import com.github.kittinunf.result.map
import mu.KLogging
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan
import kotlin.system.exitProcess

private val logger = KLogging().logger
const val REVERSE_CHAIN_ADAPTER_SERVICE_NAME = "reverse-chain-adapter"

@ComponentScan
class ReverseChainAdapterApp

fun main() {
    Result.of {
        AnnotationConfigApplicationContext(ReverseChainAdapterApp::class.java)
    }.map { context ->
        val adapter = context.getBean(ReverseChainAdapter::class.java)
        adapter.init().fold(
            { logger.info("Reverse chain-adapter has been started") },
            { ex ->
                adapter.close()
                throw ex
            })
    }.failure { ex ->
        logger.error("Cannot start reverse chain-adapter", ex)
        exitProcess(1)
    }
}
