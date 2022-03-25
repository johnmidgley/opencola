package opencola.core.model

import mu.KotlinLogging

abstract class Entity(val authorityId: Id, val entityId: Id) {
    companion object Factory {
        private val logger = KotlinLogging.logger("Entity")

        // TODO: Iterable<Fact> instead of List<Fact> for any parameters
        fun getInstance(facts: List<Fact>): Entity? {
            if (facts.isEmpty()) return null

            // TODO: Validate that all subjects and entities are equal
            // TODO: Should type be mutable? Probably no
            val typeFact = facts.lastOrNull { it.attribute == CoreAttribute.Type.spec }
                ?: throw IllegalStateException("Entity has no type")

            return when (val type = CoreAttribute.Type.spec.codec.decode(typeFact.value.bytes).toString()) {
                // TODO: Use fully qualified names
                ActorEntity::class.simpleName -> ActorEntity(facts)
                ResourceEntity::class.simpleName -> ResourceEntity(facts)
                DataEntity::class.simpleName -> DataEntity(facts)
                // TODO: Throw if not type?
                else -> {
                    logger.error { "Found unknown type: $type" }
                    null
                }
            }
        }
    }

    // TODO: Remove
    private var type by StringAttributeDelegate

    private var facts = emptyList<Fact>()
    fun getFacts(): List<Fact> {
        return facts
    }

    init {
        if (type == null)
            type = this.javaClass.simpleName
    }

    constructor(facts: List<Fact>) : this(facts.first().authorityId, facts.first().entityId) {
        if (facts.any { it.authorityId != authorityId }) {
            throw IllegalArgumentException("Attempt to construct Entity with facts from multiple authorities")
        }

        if (facts.any { it.entityId != entityId }) {
            throw IllegalArgumentException("Attempt to construct an entity with facts with multiple entity ids")
        }

        this.facts = facts
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Entity

        if (facts != other.facts) return false

        return true
    }

    override fun hashCode(): Int {
        var result = authorityId.hashCode()
        result = 31 * result + entityId.hashCode()
        result = 31 * result + facts.hashCode()
        return result
    }

    private fun getFact(propertyName: String): Pair<Attribute, Fact?> {
        val attribute = getAttributeByName(propertyName)
            ?: throw IllegalArgumentException("Attempt to access unknown property $propertyName")
        val fact = facts.lastOrNull { it.attribute == attribute }
        return Pair(attribute, fact)
    }

    internal fun getValue(propertyName: String): Value? {
        val (_, fact) = getFact(propertyName)
        return fact?.value
    }

    internal fun setValue(propertyName: String, value: Value): Fact {
        val (attribute, currentFact) = getFact(propertyName)

        if (currentFact != null) {
            if (currentFact.value == value) {
                // Fact has not changed, so no need to create a new one
                return currentFact
            }

            if (currentFact.transactionId == null) {
                throw IllegalStateException("Attempt to re-set an uncommitted value")
            }
        }

        val newFact = Fact(
            authorityId,
            entityId,
            attribute,
            value,
            if (value.bytes.isNotEmpty()) Operation.Add else Operation.Retract
        )
        facts = facts + newFact
        return newFact
    }

    fun commitFacts(epochSecond: Long, transactionId: Id) {
        val partitionedFacts = facts.partition { it.transactionId == null }
        facts = partitionedFacts.second + partitionedFacts.first.map {
            Fact(it.authorityId, it.entityId, it.attribute, it.value, it.operation, epochSecond, transactionId)
        }
    }
}
