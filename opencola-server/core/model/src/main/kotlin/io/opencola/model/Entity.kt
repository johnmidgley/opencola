package io.opencola.model

import mu.KotlinLogging
import io.opencola.model.AttributeType.*
import io.opencola.model.value.EmptyValue
import io.opencola.model.value.MultiValueListItem
import io.opencola.model.value.Value
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
    val commentIds by MultiValueSetAttributeDelegate<Id>(CoreAttribute.CommentIds.spec) //  Read only, computed property
    var attachmentIds by MultiValueSetAttributeDelegate<Id>(CoreAttribute.AttachmentIds.spec)

    private var facts = emptyList<Fact>()
    fun getAllFacts(): List<Fact> {
        return facts
    }

    fun getCurrentFacts() : List<Fact> {
        return currentFacts(facts)
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
        tags: List<String>? = null,
    ) : this(authorityId, entityId) {
        this.name = name
        this.description = description
        this.text = text
        this.imageUri = imageUri
        this.trust = trust
        this.like = like
        this.rating = rating
        tags?.let { this.tags = it }
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

    fun String.limit(maxLength: Int): String {
        return if (length > maxLength) {
            substring(0, maxLength)
        } else {
            this
        }
    }
    override fun toString(): String {
        // build string
        val sb = StringBuilder()
        sb.appendLine("Entity: $authorityId:$entityId")
        facts.forEach { f->
            val value = f.value.get().toString()
                .replace("\n", " ")
                .limit(80)
            sb.append("${f.attribute.name} | ")
            sb.append("$value | ")
            sb.append("${f.operation} | ")
            sb.append("${f.epochSecond} | ")
            sb.append("${f.transactionOrdinal} | ")
            sb.appendLine()
        }

        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Entity

        if (facts != other.facts) return false

        return true
    }

    fun diff(other: Entity): Iterable<Pair<Fact?, Fact?>> {
        fun <T>Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null

        val results = List<Pair<Fact?, Fact?>>(0) { Pair(null, null) }.toMutableList()
        val iterator1 = getAllFacts().iterator()
        val iterator2 = other.getAllFacts().iterator()
        var fact1 = if (iterator1.hasNext()) iterator1.next() else null
        var fact2 = if (iterator2.hasNext()) iterator2.next() else null

        while(fact1 != null || fact2 != null) {
            if(fact1 != fact2) {
                if (fact1 == null) {
                    results.add(Pair(null, fact2))
                    fact2 = iterator2.nextOrNull()
                } else if (fact2 == null) {
                    results.add(Pair(fact1, null))
                    fact1 = iterator1.nextOrNull()
                } else if (fact1.transactionOrdinal!! < fact2.transactionOrdinal!!) {
                    results.add(Pair(fact1, null))
                    fact1 = iterator1.nextOrNull()
                } else {
                    results.add(Pair(null, fact2))
                    fact2 = iterator2.nextOrNull()
                }
            } else {
                fact1 = iterator1.nextOrNull()
                fact2 = iterator2.nextOrNull()
            }
        }

        return results
    }

    override fun hashCode(): Int {
        var result = authorityId.hashCode()
        result = 31 * result + entityId.hashCode()
        result = 31 * result + facts.hashCode()
        return result
    }

     private fun getFact(propertyName: String, key: UUID?, value: Value<Any>?): Pair<Attribute, Fact?> {
        val attribute = Attributes.getAttributeByName(propertyName)
            ?: throw IllegalArgumentException("Attempt to access unknown property $propertyName")

        when(attribute.type){
            SingleValue -> if(key != null) throw IllegalArgumentException("Can't getFact for Single valued attribute by key")
            MultiValueList -> if(key == null) throw IllegalArgumentException("Can't getFact for List attribute without key")
            MultiValueSet -> if(key != null || value == null) throw IllegalArgumentException("getFact for MultiValueSet attribute must not have a key but have a value specified")
        }

        val fact = facts
            .lastOrNull {
                it.attribute == attribute
                        && (key == null || (it.value as MultiValueListItem<Any>).key == key)
                        && (attribute.type != MultiValueSet || value == null || it.value == value)
            }
            ?.let { if (it.operation == Operation.Add) it else null }
            ?.let {
                if (it.value == EmptyValue) {
                    // EmptyValue is only meant to be used for deleted facts
                    null
                } else it
            }

        return Pair(attribute, fact)
    }

    private fun getCurrentAttributeFacts(propertyName: String): Pair<Attribute, List<Fact>> {
        val attribute = Attributes.getAttributeByName(propertyName)
            ?: throw IllegalArgumentException("Attempt to access unknown property $propertyName")

        val factList = facts
            .asSequence()
            .filter { it.attribute == attribute }
            .groupBy {
                //  TODO: Could re-use code from entity - getAttributeFacts
                when(attribute.type){
                    SingleValue -> throw IllegalArgumentException("Cannot call getFacts for single valued properties (${attribute.name}). Call getFact instead.")
                    MultiValueSet -> it.value
                    MultiValueList -> (it.value as MultiValueListItem<Any>).key
                }
            }
            .map { (_, attributeFacts) -> attributeFacts.last() }
            .filter { it.operation != Operation.Retract }
            .toList()

        return Pair(attribute, factList)
    }

    fun getValue(propertyName: String): Value<Any>? {
        val (_, fact) = getFact(propertyName, null, null)
        return fact?.value
    }

     @Suppress("UNCHECKED_CAST")
     fun <T> getMultiValue(propertyName: String, key: UUID) : MultiValueListItem<T>? {
        val (_, fact) = getFact(propertyName, key, null)
        return fact?.let { it.value as MultiValueListItem<T> }
    }

    fun getListValues(propertyName: String): List<MultiValueListItem<Any>> {
        val (attribute, facts) = getCurrentAttributeFacts(propertyName)

        if(attribute.type != MultiValueList)
            throw IllegalArgumentException("Attempt to getListValues for non list attribute (${attribute.name})")

        return facts.map { it.value as MultiValueListItem<Any> }
    }

    fun getSetValues(propertyName: String): List<Value<Any>> {
        val (attribute, facts) = getCurrentAttributeFacts(propertyName)

        if(attribute.type != MultiValueSet)
            throw IllegalArgumentException("Attempt to getSetValues for non set attribute (${attribute.name})")

        return facts.map { it.value }
    }

    private fun valueToStore(key: UUID?, value: Value<Any>?): Value<Any> {
        // TODO: Is empty value needed?
        val storableValue = value ?: EmptyValue

        return if (key == null)
            storableValue
        else
            MultiValueListItem(key, storableValue)
    }

    private fun setValue(propertyName: String, key: UUID?, value: Value<Any>?): Fact? {
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

    internal fun deleteValue(propertyName: String, key: UUID?, value: Value<Any>?) : Fact? {
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

    internal fun setValue(propertyName: String, value: Value<Any>?) : Fact? {
        return setValue(propertyName, null, value)
    }

    internal fun setMultiValue(propertyName: String, key: UUID, value: Value<Any>?) : Fact? {
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
                MultiValueList -> Pair(fact.attribute, (fact.value as MultiValueListItem<Any>).key)
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
        fun currentFacts(facts: List<Fact>) : List<Fact> {
            if(facts.isEmpty()){
                return emptyList()
            }

            if(facts.any{ it.transactionOrdinal == null }){
                throw IllegalArgumentException("Can't compute current facts for facts that have not been committed")
            }

            val startOrdinalFact = facts.lastOrNull { it.attribute == CoreAttribute.Type.spec }
            if(startOrdinalFact == null){
                logger.error { "No type fact found for: ${facts.first().entityId}" }
                return emptyList()
            }

            if(startOrdinalFact.operation == Operation.Retract){
                return emptyList()
            }

            return facts
                .filter{ it.transactionOrdinal!! >= startOrdinalFact.transactionOrdinal!!}
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

            if(facts.map { it.authorityId }.toSet().count() != 1)
                throw IllegalArgumentException("Facts are not all from same authority")

            if(facts.map { it.entityId }.toSet().count() != 1)
                throw IllegalArgumentException("Facts are not all for the same entity")

            // TODO: Should type be mutable? Probably no
            val typeFact = currentFacts.lastOrNull { it.attribute == CoreAttribute.Type.spec }
                ?: throw IllegalStateException("Entity has no type")

            return when (val type = typeFact.value.get().toString()) {
                // TODO: Use fully qualified names
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
