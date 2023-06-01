package io.opencola.relay.common.message.store

import java.security.PublicKey

data class Usage(val receiver: PublicKey, val bytesStored: Int)