package io.opencola.model

// An entity that doesn't have a type. The allows an authority to add activity to an entity without saving it
class RawEntity : Entity {
    constructor(authorityId: Id, entityId: Id) : super(authorityId, entityId)
    constructor(facts: List<Fact>) : super(facts)

    fun setType(type: String) : Entity {
        this.type = type
        return fromFacts(getAllFacts())!!
    }
}
