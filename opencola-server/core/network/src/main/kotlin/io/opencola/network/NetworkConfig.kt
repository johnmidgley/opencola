package io.opencola.network

import java.net.URI

data class SocksProxy(val host: String, val port: Int)

data class NetworkConfig(val defaultAddress: URI = URI("ocr://relay.opencola.net"),
                         val requestTimeoutMilliseconds: Long = 20000,
                         val socksProxy: SocksProxy? = null,
                         val offlineMode: Boolean = false)