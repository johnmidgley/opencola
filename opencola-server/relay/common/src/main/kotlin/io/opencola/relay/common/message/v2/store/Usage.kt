package io.opencola.relay.common.message.v2.store

import java.security.PublicKey

data class Usage(val receiver: PublicKey, val bytesStored: Int)