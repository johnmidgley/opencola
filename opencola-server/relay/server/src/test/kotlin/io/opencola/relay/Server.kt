package io.opencola.relay

import java.net.URI

val localRelayServerUri = URI("ocr://0.0.0.0")
val prodRelayServerUri = URI("ocr://relay.opencola.net")

private var baseServerPort = 6000
fun getServerUri(port: Int) = URI("ocr://0.0.0.0:$port")
fun getNewServerUri() = getServerUri(baseServerPort++)