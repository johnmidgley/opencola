package opencola.core.model

import opencola.core.extensions.ifNotNullOrElse
import opencola.core.extensions.nullOrElse
import opencola.core.serialization.*
import java.net.URI
import java.security.PublicKey
import kotlin.reflect.KProperty

class AttributeDelegate<T>(val codec: ByteArrayCodec<T>, val resettable: Boolean = true) {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): T? {
        return thisRef.getValue(property.name)?.bytes.nullOrElse { codec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: T?) {
        if(!resettable && getValue(thisRef, property) != null)
             throw IllegalStateException("Attempt to reset a non resettable property: ${property.name}")

        thisRef.setValue(property.name, value.ifNotNullOrElse( { Value(codec.encode(it as T)) }, { Value.emptyValue }))
    }
}

// TODO - validate all fields (i.e. ratings should be between 0 and 1.0)
val nonResettableIdAttributeDelegate = AttributeDelegate(Id.Factory, false)
val booleanAttributeDelegate = AttributeDelegate(BooleanByteArrayCodec)
val floatAttributeDelegate = AttributeDelegate(FloatByteArrayCodec)
val stringAttributeDelegate = AttributeDelegate(StringByteArrayCodec)
val idAttributeDelegate = AttributeDelegate(Id.Factory)
val uriAttributeDelegate = AttributeDelegate(UriByteArrayCodec, false)
val setOfStringAttributeDelegate = AttributeDelegate(SetOfStringByteArrayCodec)
val publicKeyAttributeDelegate = AttributeDelegate(PublicKeyByteArrayCodec)

object MultiValueSetOfIdAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): List<Id> {
        return thisRef.getSetValues(property.name).map { Id.decode(it.bytes) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: List<Id>) {
        // Update any present values
        value.forEach { id ->
            thisRef.setValue(property.name, Value(Id.encode(id)))
        }

        // Delete any removed values
        thisRef
            .getSetValues(property.name).map { Id.decode(it.bytes) }
            .toSet()
            .minus(value.toSet())
            .forEach { thisRef.deleteValue(property.name, null, Value(Id.encode(it))) }
    }
}

object MultiValueListOfStringAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): List<MultiValueListOfStringItem> {
        return thisRef.getListValues(property.name).map { MultiValueListOfStringItem.fromMultiValue(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: List<MultiValueListOfStringItem>) {
        // Update any present values
        value.forEach { multiValueString ->
            thisRef.setMultiValue(
                property.name,
                multiValueString.key,
                multiValueString.value?.toByteArray().nullOrElse { Value(it) })
        }

        // Delete any removed values
        thisRef
            .getListValues(property.name).map { it.key }
            .toSet()
            .minus(value.map { it.key }.toSet())
            .forEach { thisRef.deleteValue(property.name, it, null) }
    }
}