package opencola.server.handlers

import io.opencola.model.Id
import io.opencola.util.Base58

class Context(val personaIds: Set<Id>) {
    constructor(context: String?) : this (
         context?.let { String(Base58.decode(it)) }
            ?.split(",")
            ?.filter{ it.isNotBlank() }
            ?.map { Id.decode(it) }
            ?.toSet()
            ?: emptySet()
    )

    constructor(vararg personaIds: Id) : this(personaIds.toSet())

    override fun toString(): String {
        return personaIds.joinToString(",").toByteArray().let { Base58.encode(it) }
    }
}