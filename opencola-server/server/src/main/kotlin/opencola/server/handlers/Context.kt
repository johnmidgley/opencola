package opencola.server.handlers

import io.opencola.model.Id

class Context(private val personaIds: Set<Id>) {
    constructor(context: String?) : this (
        context
            ?.split(",")
            ?.filter{ it.isNotBlank() }
            ?.map { Id.decode(it) }
            ?.toSet()
            ?: emptySet()
    )

    constructor(vararg personaIds: Id) : this(personaIds.toSet())

    fun getPersonaIds(vararg defaultPersonaIds: Id): Set<Id> {
        return personaIds.ifEmpty { defaultPersonaIds.toSet() }
    }
}