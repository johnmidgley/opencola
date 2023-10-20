package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id

data class Usage(val receiver: Id, val bytesStored: Int)