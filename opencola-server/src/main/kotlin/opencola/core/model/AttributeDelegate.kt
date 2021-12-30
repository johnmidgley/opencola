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
object BooleanAttributeDelegate : ByteArrayCodec {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): Boolean? {
        return decode(thisRef.getValue(property.name))
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: Boolean?) {
        thisRef.setValue(property.name, encode(value))
    }

    private const val FALSE = 0
    private const val TRUE = 1

    override fun encode(value: Any?): ByteArray {
        return ByteBuffer.allocate(4).putInt(if (value as Boolean) TRUE else FALSE).array()
    }

    override fun decode(value: ByteArray?): Boolean? {
        return value.nullOrElse { ByteBuffer.wrap(value).int == TRUE  }
    }
}

object FloatAttributeDelegate : ByteArrayCodec {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): Float? {
        return decode(thisRef.getValue(property.name))
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: Float?) {
        thisRef.setValue(property.name, encode(value))
    }

    override fun encode(value: Any?): ByteArray {
        return ByteBuffer.allocate(4).putFloat(value as Float).array()
    }

    override fun decode(value: ByteArray?): Float? {
        return value.nullOrElse { ByteBuffer.wrap(it).float }
    }
}

object StringAttributeDelegate : ByteArrayCodec {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): String? {
        return decode(thisRef.getValue(property.name))
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: String?) {
        thisRef.setValue(property.name, encode(value))
    }

    override fun encode(value: Any?): ByteArray {
        return (value as String).toByteArray()
    }

    override fun decode(value: ByteArray?): String? {
        return value.nullOrElse { String(it) }
    }
}

object IdAttributeDelegate : ByteArrayCodec {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): Id? {
        return decode(thisRef.getValue(property.name))
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: Id?) {
        thisRef.setValue(property.name, encode(value))
    }

    override fun encode(value: Any?): ByteArray {
        return Id.encode(value)
    }

    override fun decode(value: ByteArray?): Id? {
        return Id.decode(value) as Id?
    }
}

object UriAttributeDelegate : ByteArrayCodec {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): URI? {
        return decode(thisRef.getValue(property.name))
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: URI?) {
        thisRef.setValue(property.name, encode(value))
    }

    override fun encode(value: Any?): ByteArray {
        return (value as URI).toString().toByteArray()
    }

    override fun decode(value: ByteArray?): URI? {
        return value.nullOrElse { URI(String(it)) }
    }
}

object SetOfStringAttributeDelegate : ByteArrayCodec {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): Set<String>? {
        // TODO: Is there anything that can be done here, given Type erasure? Probably make tags class...
        return decode(thisRef.getValue(property.name))
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: Set<String>?) {
        thisRef.setValue(property.name, encode(value))
    }

    override fun encode(value: Any?): ByteArray {
        // TODO: Get rid of json encoder.
        return Json.encodeToString(value as Set<String>).toByteArray()
    }

    override fun decode(value: ByteArray?): Set<String>? {
        return value.nullOrElse { Json.decodeFromString(String(it)) }
    }
}

object PublicKeyAttributeDelegate : ByteArrayCodec {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): PublicKey? {
        return decode(thisRef.getValue(property.name))
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: PublicKey?) {
        thisRef.setValue(property.name, encode(value))
    }

    override fun encode(value: Any?): ByteArray {
        return (value as PublicKey).encoded
    }

    override fun decode(value: ByteArray?): PublicKey? {
        return value.nullOrElse { publicKeyFromBytes(it) }
    }
}