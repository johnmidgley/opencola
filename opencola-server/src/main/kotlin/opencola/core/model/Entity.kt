package opencola.core.model

import mu.KotlinLogging
import opencola.core.extensions.nullOrElse
import opencola.core.model.AttributeType.*
import java.util.*

abstract class Entity(val authorityId: Id, val entityId: Id) {
    companion object Factory {
        private val logger = KotlinLogging.logger("Entity")

        private fun currentFacts(facts: Iterable<Fact>) : List<Fact> {
            // Assumes facts have been sorted by transactionOrdinal
            // TODO: Won't work for multivalued attributes - fix when supported
            return facts
                .groupBy { it.attribute }
                .map{ it.value.last() }
                .filter { it.operation != Operation.Retract }
        }

        // TODO: Iterable<Fact> instead of List<Fact> for any parameters
        fun fromFacts(facts: List<Fact>): Entity? {
            val sortedFacts = facts.sortedBy { it.transactionOrdinal ?: Long.MAX_VALUE }
            val currentFacts = currentFacts(sortedFacts)
            if (currentFacts.isEmpty()) return null

            // TODO: Validate that all subjects and entities are equal
            // TODO: Should type be mutable? Probably no
            val typeFact = currentFacts.lastOrNull { it.attribute == CoreAttribute.Type.spec }
                ?: throw IllegalStateException("Entity has no type")

            return when (val type = CoreAttribute.Type.spec.codec.decode(typeFact.value.bytes).toString()) {
                // TODO: Use fully qualified names
                ActorEntity::class.simpleName -> ActorEntity(sortedFacts)
                ResourceEntity::class.simpleName -> ResourceEntity(sortedFacts)
                DataEntity::class.simpleName -> DataEntity(sortedFacts)
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

        this.facts = facts.sortedBy { it.transactionOrdinal }
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

    // For multi-valued lists
     private fun getFact(propertyName: String, key: UUID?, value: Value?): Pair<Attribute, Fact?> {
        val attribute = getAttributeByName(propertyName)
            ?: throw IllegalArgumentException("Attempt to access unknown property $propertyName")

        when(attribute.type){
            SingleValue -> if(key != null) throw IllegalArgumentException("Can't getFact for Single valued attribute by key")
            MultiValueList -> if(key == null) throw IllegalArgumentException("Can't getFact for List attribute without key")
            MultiValueSet -> if(key != null) throw IllegalArgumentException("Can't getFact for Set attribute by key")
        }

        val fact = facts
            .lastOrNull { it.attribute == attribute
                    && (key == null || MultiValue.keyOf(it.value) == key)
                    && (value == null || it.value == value)
            }
            .nullOrElse { if(it.operation == Operation.Add) it else null }

        return Pair(attribute, fact)
    }

    // Only for Multi-value attributes
    private fun getFacts(propertyName: String): Pair<Attribute, List<Fact>> {
        val attribute = getAttributeByName(propertyName)
            ?: throw IllegalArgumentException("Attempt to access unknown property $propertyName")

        if(attribute.type == SingleValue)
            throw IllegalArgumentException("Cannot call getFacts for single valued properties (${attribute.name}). Call getFact instead.")

        val factList = facts
            .asSequence()
            .filter { it.attribute == attribute }
            .groupBy { if(attribute.type == MultiValueList) MultiValue.keyOf(it.value) else it.value }
            .map { it.value.last() }
            .filter { it.operation != Operation.Retract }
            .toList()

        return Pair(attribute, factList)
    }

    internal fun getValue(propertyName: String): Value? {
        val (_, fact) = getFact(propertyName, null, null)
        return fact?.value
    }

    internal fun getMultiValue(propertyName: String, key: UUID) : MultiValue? {
        val (_, fact) = getFact(propertyName, key, null)
        return fact.nullOrElse { MultiValue.fromValue(it.value) }
    }

    internal fun getMultiValues(propertyName: String): List<MultiValue> {
        val (_, facts) = getFacts(propertyName)
        return facts.map { MultiValue.fromValue(it.value) }
    }

    private fun valueToStore(key: UUID?, value: Value?) : Value {
        val storableValue = value ?: Value.emptyValue

        if(key == null)
            return storableValue

        return MultiValue(key, storableValue.bytes).toValue()
    }

    private fun setValue(propertyName: String, key: UUID?, value: Value?): Fact {
        val (attribute, currentFact) = getFact(propertyName, key, null)

        if (currentFact != null) {
            if (currentFact.value == value) {
                // Fact has not changed, so no need to create a new one
                return currentFact
            }

            if (currentFact.transactionOrdinal == null) {
                throw IllegalStateException("Attempt to re-set an uncommitted value")
            }
        }

        val newFact = Fact(
            authorityId,
            entityId,
            attribute,
            valueToStore(key, value),
            if (value == null) Operation.Retract else Operation.Add
        )
        facts = facts + newFact
        return newFact
    }

    internal fun setValue(propertyName: String, value: Value?) : Fact {
        return setValue(propertyName, null, value)
    }

    internal fun setMultiValue(propertyName: String, key: UUID, value: Value?) : Fact {
        return setValue(propertyName, key, value)
    }

    // NOT Great. Decoupled from actual factions that were persisted.
    fun commitFacts(epochSecond: Long, transactionOrdinal: Long) {
        val partitionedFacts = facts.partition { it.transactionOrdinal == null }
        facts = partitionedFacts.second + partitionedFacts.first.map {
            Fact(it.authorityId, it.entityId, it.attribute, it.value, it.operation, epochSecond, transactionOrdinal)
        }
    }
}
