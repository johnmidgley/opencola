package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class Usage(@Contextual val to: Id, val numMessages: Int, val numBytes: Long)