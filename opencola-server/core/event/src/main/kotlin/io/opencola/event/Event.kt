package io.opencola.event

import java.time.Instant

class Event(
    val id: Long,
    val name: String,
    val data: ByteArray,
    val attempt: Int = 0,
    val epochSecond: Long = Instant.now().epochSecond,
) {
    override fun toString(): String {
        return "Message(id=${this.id}, name=${this.name}, data=${data.size} bytes, attempt=$attempt, epochSecond=$epochSecond)"
    }
}