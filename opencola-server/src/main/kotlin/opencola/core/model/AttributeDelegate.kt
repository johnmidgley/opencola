package opencola.core.model

import opencola.core.extensions.ifNotNullOrElse
import opencola.core.extensions.nullOrElse
import opencola.core.serialization.*
import java.net.URI
import java.security.PublicKey
import kotlin.reflect.KProperty


// TODO - validate all fields (i.e. ratings should be between 0 and 1.0)
// TODO - Only allow update if value has changed - otherwise ignore (i.e. get before setting)
object BooleanAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): Boolean? {
        // TODO - Can these two lines be collapsed (in all delegates)
        // TODO: Can we abstract more - the only difference between these delegates is the encode / decode expression
        val value = thisRef.getValue(property.name)?.bytes
        return value.nullOrElse { BooleanByteArrayCodec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: Boolean?) {
        thisRef.setValue(property.name, value.ifNotNullOrElse( { Value(BooleanByteArrayCodec.encode(value as Boolean)) }, { Value.emptyValue }))
    }
}

object FloatAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): Float? {
        val value = thisRef.getValue(property.name)?.bytes
        return value.nullOrElse { FloatByteArrayCodec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: Float?) {
        thisRef.setValue(property.name, value.ifNotNullOrElse( { Value(FloatByteArrayCodec.encode(value as Float)) }, { Value.emptyValue }))
    }
}

object StringAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): String? {
        val value = thisRef.getValue(property.name)?.bytes
        return value.nullOrElse { StringByteArrayCodec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: String?) {
        thisRef.setValue(property.name, value.ifNotNullOrElse( { Value(StringByteArrayCodec.encode(value as String)) }, { Value.emptyValue }))
    }
}

object IdAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): Id? {
        val value = thisRef.getValue(property.name)?.bytes
        return value.nullOrElse { Id.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: Id?) {
        thisRef.setValue(property.name, value.ifNotNullOrElse( { Value(Id.encode(value as Id)) }, { Value.emptyValue }))
    }
}

object UriAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): URI? {
        val value = thisRef.getValue(property.name)?.bytes
        return value.nullOrElse { UriByteArrayCodec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: URI?) {
        thisRef.setValue(property.name, value.ifNotNullOrElse( { Value(UriByteArrayCodec.encode(value as URI)) }, { Value.emptyValue }))
    }
}

object SetOfStringAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): Set<String>? {
        // TODO: Is there anything that can be done here, given Type erasure? Probably make tags class...
        val value = thisRef.getValue(property.name)?.bytes
        return value.nullOrElse { SetOfStringByteArrayCodec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: Set<String>?) {
        thisRef.setValue(property.name, value.ifNotNullOrElse( { Value(SetOfStringByteArrayCodec.encode(value as Set<String>)) }, { Value.emptyValue }))
    }
}

object PublicKeyAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): PublicKey? {
        val value = thisRef.getValue(property.name)?.bytes
        return value.nullOrElse { PublicKeyByteArrayCodec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: PublicKey?) {
        thisRef.setValue(property.name, value.ifNotNullOrElse( { Value(PublicKeyByteArrayCodec.encode(value as PublicKey)) }, { Value.emptyValue }))
    }
}

object MultiValueStringAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): List<MultiValueString> {
        return thisRef.getMultiValues(property.name).map { MultiValueString.fromMultiValue(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: List<MultiValueString>) {
        // Update any present values
        value.forEach { multiValueString ->
            thisRef.setMultiValue(
                property.name,
                multiValueString.key,
                multiValueString.value?.toByteArray().nullOrElse { Value(it) })
        }

        // Delete any removed values
        thisRef
            .getMultiValues(property.name).map { it.key }
            .toSet()
            .minus(value.map { it.key }.toSet())
            .forEach { thisRef.setMultiValue(property.name, it, null) }
    }
}