package opencola.core.model

import java.lang.IllegalArgumentException
import java.net.URI
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

// Types of entities
// Producer
// Consumer
// Resource?
//      Product (ISBN)
//      Page
//      Document
//      Video


// Local entity?
// Larger scope entities could have computed values (likes, trust, etc), but to what scope? Need to figure this out!
// Reference entities vs. data entities?
// Actors vs. object - artifacts cannot take action on anything, they just exist.
// Artifacts can be data or external (products, etc.)

abstract class Entity(val authorityId: Id, val entityId: Id){
    companion object Factory {
        fun getInstance(facts: List<Fact>) : Entity? {
            if(facts.isEmpty()) return null

            // TODO: Validate that all subjects and entities are equal
            // TODO: Should type be mutable? Probably no
            val typeFact = facts.lastOrNull { it.attribute == CoreAttribute.Type.spec }
                ?: throw IllegalStateException("Entity has no type")

            return when(CoreAttribute.Type.spec.codec.decode(typeFact.value.bytes).toString()){
                // TODO: Use fully qualified names
                ActorEntity::class.simpleName -> ActorEntity(facts)
                // Authority::class.simpleName -> Authority(facts)
                ResourceEntity::class.simpleName -> ResourceEntity(facts)
                DataEntity::class.simpleName -> DataEntity(facts)
                // TODO: Throw if not type?
                else -> null
            }
        }
    }

    // TODO: Remove
    private var type by StringAttributeDelegate

    private var facts = emptyList<Fact>()
    fun getFacts() : List<Fact>{
        return facts
    }

    init{
        if(type == null)
            type = this.javaClass.simpleName
    }

    constructor(facts: List<Fact>) : this(facts.first().authorityId, facts.first().entityId) {
        if(facts.any{ it.authorityId != authorityId}){
            throw IllegalArgumentException("Attempt to construct Entity with facts from multiple subjects")
        }

        if(facts.any{ it.entityId != entityId }){
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

    private fun getFact(propertyName: String) : Pair<Attribute, Fact?> {
        val attribute = getAttributeByName(propertyName) ?: throw IllegalArgumentException("Attempt to access unknown property $propertyName")
        val fact =  facts.lastOrNull{ it.attribute == attribute }
        return Pair(attribute, fact)
    }

    internal fun getValue(propertyName: String) : Value? {
        val (_, fact) = getFact(propertyName)
        return fact?.value
    }

    internal fun setValue(propertyName: String, value: Value) : Fact {
        val (attribute, currentFact) = getFact(propertyName)

        if(currentFact != null){
            if(currentFact.value == value) {
                // Fact has not changed, so no need to create a new one
                return currentFact
            }

            if(currentFact.transactionId == UNCOMMITTED){
                throw IllegalStateException("Attempt to re-set an uncommitted value")
            }
        }

        val newFact = Fact(authorityId, entityId, attribute, value, if (value.bytes.isNotEmpty()) Operation.Add else Operation.Retract)
        facts = facts + newFact
        return newFact
    }
}

// Person or website (organization), identified by hash of public key
//  TODO: Figure out how to properly initialize and store publicKey attribute
// Could probably clean this up with delegated properties
open class ActorEntity : Entity {
    var uri by UriAttributeDelegate
    var imageUri by UriAttributeDelegate
    var name by StringAttributeDelegate
    var description by StringAttributeDelegate
    var publicKey by PublicKeyAttributeDelegate
    var trust by FloatAttributeDelegate
    var tags by SetOfStringAttributeDelegate
    var like by BooleanAttributeDelegate
    var rating by FloatAttributeDelegate

    constructor(authorityId: Id,
                publicKey: PublicKey,
                uri: URI? = null,
                imageUri: URI? = null,
                name: String? = null,
                description: String? = null,
                trust: Float? = null,
                tags: Set<String>? = null,
                like: Boolean? = null,
                rating: Float? = null,
    ) : super(authorityId, Id.ofPublicKey(publicKey)){
        // Null checks are more efficient, but more importantly, don't result in retracting facts
        if(uri != null) this.uri = uri
        if(imageUri != null) this.imageUri = imageUri
        if(name != null) this.name = name
        if(description != null) this.description = description
        this.publicKey = publicKey
        if(trust != null) this.trust = trust
        if(tags != null) this.tags = tags
        if(like != null) this.like = like
        if(rating != null) this.rating = rating
    }

    constructor(facts: List<Fact>) : super(facts)
}

class Authority(keyPair: KeyPair, uri: URI? = null, imageUri: URI? = null, name: String? = null, description: String? = null,
                trust: Float? = null, tags: Set<String>? = null,  like: Boolean? = null, rating: Float? = null)
        : ActorEntity(Id.ofPublicKey(keyPair.public), keyPair.public, uri, imageUri, name, description, trust, tags, like, rating) {
    // TODO: Private key probably shouldn't be here
    private val privateKey : PrivateKey

    init {
        privateKey = keyPair.private
    }

    // TODO: Access the private key only when signing. Does not need to be stored in the entity.
    fun signTransaction(transaction: Transaction): SignedTransaction {
        return transaction.sign(privateKey)
    }
}

open class ResourceEntity : Entity {
    var uri by UriAttributeDelegate
    var dataId by IdAttributeDelegate
    var name by StringAttributeDelegate
    var description by StringAttributeDelegate
    var text by StringAttributeDelegate
    var imageUri by UriAttributeDelegate
    var trust by FloatAttributeDelegate
    var tags by SetOfStringAttributeDelegate
    var like by BooleanAttributeDelegate
    var rating by FloatAttributeDelegate

    constructor(authorityId: Id,
                uri: URI,
                name: String? = null,
                description: String? = null,
                text: String? = null,
                imageUri: URI? = null,
                trust: Float? = null,
                tags: Set<String>? = null,
                like: Boolean? = null,
                rating: Float? = null,
    ) : super(authorityId, Id.ofUri(uri)){
        // Null checks are more efficient, but more importantly, don't result in retracting facts
        this.uri = uri
        if(name != null) this.name = name
        if(description != null) this.description = description
        if(text != null) this.text = text
        if(imageUri != null) this.imageUri = imageUri
        if(trust != null) this.trust = trust
        if(tags != null) this.tags = tags
        if(like != null) this.like = like
        if(rating != null) this.rating = rating
    }

    constructor(facts: List<Fact>) : super(facts)
}

// doc.pdf
// id - hash of data
// source - uri source
// parent - id of parent (container or website)
// name, desc, tags, trust, like, rating
open class DataEntity : Entity {
    constructor(authorityId: Id, dataId: Id, mimeType: String) : super(authorityId, dataId){
        this.mimeType = mimeType
    }
    constructor(facts: List<Fact>) : super(facts)

    // URI where data was originally fetched
    var mimeType by StringAttributeDelegate
}
