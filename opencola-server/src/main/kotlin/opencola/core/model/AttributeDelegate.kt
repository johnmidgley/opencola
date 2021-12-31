package opencola.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import opencola.core.extensions.nullOrElse
import opencola.core.security.publicKeyFromBytes
import java.net.URI
import java.nio.ByteBuffer
import java.security.PublicKey
import kotlin.reflect.KProperty


// TODO - validate all fields (i.e. ratings should be between 0 and 1.0)
// TODO - Only allow update if value has changed - otherwise ignore (i.e. get before setting)
object BooleanAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): Boolean? {
        // TODO - Can these two lines be collapsed (in all delegates)
        // TODO: Can we abstract more - the only difference between these delegates is the encode / decode expression
        val value = thisRef.getValue(property.name)?.value
        return value.nullOrElse { BooleanByteArrayCodec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: Boolean?) {
        thisRef.setValue(property.name, value.nullOrElse { Value(BooleanByteArrayCodec.encode(value as Boolean)) })
    }
}

object FloatAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): Float? {
        val value = thisRef.getValue(property.name)?.value
        return value.nullOrElse { FloatByteArrayCodec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: Float?) {
        thisRef.setValue(property.name, value.nullOrElse { Value(FloatByteArrayCodec.encode(value as Float)) })
    }
}

object StringAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): String? {
        val value = thisRef.getValue(property.name)?.value
        return value.nullOrElse { StringByteArrayCodec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: String?) {
        thisRef.setValue(property.name, value.nullOrElse { Value(StringByteArrayCodec.encode(value as String)) })
    }
}

object IdAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): Id? {
        val value = thisRef.getValue(property.name)?.value
        return value.nullOrElse { Id.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: Id?) {
        thisRef.setValue(property.name, value.nullOrElse { Value(Id.encode(value as Id)) })
    }
}

object UriAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): URI? {
        val value = thisRef.getValue(property.name)?.value
        return value.nullOrElse { UriByteArrayCodec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: URI?) {
        thisRef.setValue(property.name, value.nullOrElse { Value(UriByteArrayCodec.encode(value as URI)) })
    }
}

object SetOfStringAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): Set<String>? {
        // TODO: Is there anything that can be done here, given Type erasure? Probably make tags class...
        val value = thisRef.getValue(property.name)?.value
        return value.nullOrElse { SetOfStringByteArrayCodec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: Set<String>?) {
        thisRef.setValue(property.name, value.nullOrElse { Value(SetOfStringByteArrayCodec.encode(value as Set<String>)) })
    }
}

object PublicKeyAttributeDelegate {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): PublicKey? {
        val value = thisRef.getValue(property.name)?.value
        return value.nullOrElse { PublicKeyByteArrayCodec.decode(it) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: PublicKey?) {
        thisRef.setValue(property.name, value.nullOrElse { Value(PublicKeyByteArrayCodec.encode(value as PublicKey)) })
    }
}