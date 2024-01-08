package io.opencola.event.log

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class EventLogEntry(
    val id: String,
    val name: String,
    val timeMilliseconds: Long,
    val parameters: Map<String, String> = emptyMap(),
    val message: String? = null,
) {
    constructor(name: String, parameters: Map<String, String> = emptyMap(), message: String? = null) : this(
        UUID.randomUUID().toString(),
        name,
        System.currentTimeMillis(),
        parameters,
        message,
    )

    val uuid: UUID
        get() = UUID.fromString(id)
}