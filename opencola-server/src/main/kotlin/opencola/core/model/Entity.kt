package opencola.core.model

import mu.KotlinLogging
import opencola.core.extensions.nullOrElse
import opencola.core.model.AttributeType.*
import java.net.URI
import java.util.*


abstract class Entity(val authorityId: Id, val entityId: Id) {
    private var type by stringAttributeDelegate
    var name by stringAttributeDelegate
    var description by stringAttributeDelegate
    var text by stringAttributeDelegate
    var imageUri by imageUriAttributeDelegate
    var trust by floatAttributeDelegate
    var like by booleanAttributeDelegate
    var rating by floatAttributeDelegate
    var tags by tagsAttributeDelegate
    val commentIds by MultiValueSetOfIdAttributeDelegate // Read only, computed property

    private var facts = emptyList<Fact>()
    fun getAllFacts(): List<Fact> {
        return facts
    }

    fun getCurrentFacts() : List<Fact> {
        return currentFacts(facts)
    }

    fun getNonRetractedFacts() : List<Fact> {
        return nonRetractedFacts(facts)
    }

    init {
        if (type == null)
            type = this.javaClass.simpleName
    }

    constructor(
        authorityId: Id,
        entityId: Id,
        name: String? = null,
        description: String? = null,
        text: String? = null,
        imageUri: URI? = null,
        trust: Float? = null,
        like: Boolean? = null,
        rating: Float? = null,
        tags: Set<String>? = null,
    ) : this(authorityId, entityId) {
        this.name = name
        this.description = description
        this.text = text
        this.imageUri = imageUri
        this.trust = trust
        this.like = like
        this.rating = rating
        tags.nullOrElse { this.tags = it }
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

     private fun getFact(propertyName: String, key: UUID?, value: Value?): Pair<Attribute, Fact?> {
        val attribute = getAttributeByName(propertyName)
            ?: throw IllegalArgumentException("Attempt to access unknown property $propertyName")

        when(attribute.type){
            SingleValue -> if(key != null) throw IllegalArgumentException("Can't getFact for Single valued attribute by key")
            MultiValueList -> if(key == null) throw IllegalArgumentException("Can't getFact for List attribute without key")
            MultiValueSet -> if(key != null || value == null) throw IllegalArgumentException("getFact for MultiValueSet attribute must not have a key but have a value specified")
        }

        val fact = facts
            .lastOrNull { it.attribute == attribute
                    && (key == null || MultiValueListItem.keyOf(it.value) == key)
                    && (attribute.type != MultiValueSet || value == null || it.value == value)
            }
            .nullOrElse { if(it.operation == Operation.Add) it else null }

        return Pair(attribute, fact)
    }

    private fun getCurrentAttributeFacts(propertyName: String): Pair<Attribute, List<Fact>> {
        val attribute = getAttributeByName(propertyName)
            ?: throw IllegalArgumentException("Attempt to access unknown property $propertyName")

        val factList = facts
            .asSequence()
            .filter { it.attribute == attribute }
            .groupBy {
                //  TODO: Could re-use code from entity - getAttributeFacts
                when(attribute.type){
                    SingleValue -> throw IllegalArgumentException("Cannot call getFacts for single valued properties (${attribute.name}). Call getFact instead.")
                    MultiValueSet -> it.value
                    MultiValueList -> MultiValueListItem.keyOf(it.value)
                }
            }
            .map { (_, attributeFacts) -> attributeFacts.last() }
            .filter { it.operation != Operation.Retract }
            .toList()

        return Pair(attribute, factList)
    }

    internal fun getValue(propertyName: String): Value? {
        val (_, fact) = getFact(propertyName, null, null)
        return fact?.value
    }

    internal fun getMultiValue(propertyName: String, key: UUID) : MultiValueListItem? {
        val (_, fact) = getFact(propertyName, key, null)
        return fact.nullOrElse { MultiValueListItem.fromValue(it.value) }
    }

    internal fun getListValues(propertyName: String): List<MultiValueListItem> {
        val (attribute, facts) = getCurrentAttributeFacts(propertyName)

        if(attribute.type != MultiValueList)
            throw IllegalArgumentException("Attempt to getListValues for non list attribute (${attribute.name})")

        return facts.map { MultiValueListItem.fromValue(it.value) }
    }

    internal fun getSetValues(propertyName: String): List<Value> {
        val (attribute, facts) = getCurrentAttributeFacts(propertyName)

        if(attribute.type != MultiValueSet)
            throw IllegalArgumentException("Attempt to getSetValues for non set attribute (${attribute.name})")

        return facts.map { it.value }
    }

    private fun valueToStore(key: UUID?, value: Value?): Value {
        val storableValue = value ?: Value.emptyValue

        return if (key == null)
            storableValue
        else
            MultiValueListItem(key, storableValue.bytes).toValue()
    }

    private fun setValue(propertyName: String, key: UUID?, value: Value?): Fact? {
        val (attribute, currentFact) = getFact(propertyName, key, value)

        if(currentFact == null){
            if(value == null)
                // Setting null on a non-existing fact does nothing
                return null
        }
        else {
            if (currentFact.value == value) {
                // Fact has not changed, so no need to create a new one
                return currentFact
            }

            if (currentFact.transactionOrdinal == null) {
                // Remove uncommitted fact, since it's being overwritten
                facts = facts.minus(currentFact)
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

    internal fun deleteValue(propertyName: String, key: UUID?, value: Value?) : Fact? {
        val (attribute, fact) = getFact(propertyName, key, value)

        if(fact != null && fact.operation != Operation.Retract){
            val newFact = Fact(
                authorityId,
                entityId,
                attribute,
                valueToStore(key, value),
                Operation.Retract
            )
            facts = facts + newFact
            return newFact
        }

        return null
    }

    internal fun setValue(propertyName: String, value: Value?) : Fact? {
        return setValue(propertyName, null, value)
    }

    internal fun setMultiValue(propertyName: String, key: UUID, value: Value?) : Fact? {
        return setValue(propertyName, key, value)
    }

    // NOT Great. Decoupled from actual facts that were persisted. Take list of facts instead?
    fun commitFacts(epochSecond: Long, transactionOrdinal: Long) : Iterable<Fact> {
        val (uncommittedFacts, committedFacts) = facts.partition { it.transactionOrdinal == null }

        val newCommittedFacts = uncommittedFacts.map {
            Fact(it.authorityId, it.entityId, it.attribute, it.value, it.operation, epochSecond, transactionOrdinal)
        }

        facts = committedFacts + newCommittedFacts
        return newCommittedFacts
    }

    companion object Factory {
        private val logger = KotlinLogging.logger("Entity")

        private val attributeGroupingKey: (Fact) -> Any? = { fact ->
            when (fact.attribute.type) {
                SingleValue -> fact.attribute
                MultiValueSet -> Pair(fact.attribute, fact.value)
                MultiValueList -> Pair(fact.attribute,MultiValueListItem.keyOf(fact.value))
            }
        }

        // Assumes facts have been sorted by transactionOrdinal
        private fun headAttributeFacts(attribute: Attribute, facts: List<Fact>) : List<Fact> {
            return facts
                .filter { it.attribute == attribute }
                .groupBy(attributeGroupingKey)
                .map { it.value.last() }
        }

        // Assumes facts have been sorted by transactionOrdinal
        fun currentFacts(facts: Iterable<Fact>) : List<Fact> {
            return facts
                .groupBy { it.attribute }
                .flatMap { headAttributeFacts(it.key, it.value) }
                .filter { it.operation != Operation.Retract }
        }

        // Assumes facts have been sorted by transactionOrdinal
        fun nonRetractedFacts(facts: Iterable<Fact>) : List<Fact> {
            return facts
                .groupBy(attributeGroupingKey)
                .flatMap { (_, value) ->
                    value
                        .reversed()
                        .takeWhile { it.operation != Operation.Retract }
                        .reversed()
                }
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
                Authority::class.simpleName -> Authority(sortedFacts)
                ResourceEntity::class.simpleName -> ResourceEntity(sortedFacts)
                DataEntity::class.simpleName -> DataEntity(sortedFacts)
                CommentEntity::class.simpleName -> CommentEntity(sortedFacts)
                PostEntity::class.simpleName -> PostEntity(sortedFacts)
                // TODO: Throw if not type? Configure throw on error for debugging?
                else -> {
                    throw RuntimeException("Found unknown type: $type")
                }
            }
        }
    }
}
