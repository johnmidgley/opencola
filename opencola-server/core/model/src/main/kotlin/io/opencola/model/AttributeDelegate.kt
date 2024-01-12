package io.opencola.model

import io.opencola.model.value.*
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST")
class AttributeDelegate<T>(val valueWrapper: ValueWrapper<T>, val resettable: Boolean = true) {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): T? {
        return thisRef.getValue(property.name)?.let { valueWrapper.unwrap(it as Value<T>) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: T?) {
        if(!resettable && getValue(thisRef, property) != null)
             throw IllegalStateException("Attempt to reset a non resettable property: ${property.name}")

        thisRef.setValue(property.name, value?.let { valueWrapper.wrap(it) as Value<Any> })
    }
}

// TODO - validate all fields (i.e. ratings should be between 0 and 1.0)
val nonResettableIdAttributeDelegate = AttributeDelegate(IdValue, false)
val booleanAttributeDelegate = AttributeDelegate(BooleanValue)
val intAttributeDelegate = AttributeDelegate(IntValue)
val floatAttributeDelegate = AttributeDelegate(FloatValue)
val nonResettableStringValueDelegate = AttributeDelegate(StringValue, false)
val stringAttributeDelegate = AttributeDelegate(StringValue)
val nonResettableUriAttributeDelegate = AttributeDelegate(UriValue, false)
val uriAttributeDelegate = AttributeDelegate(UriValue)
val imageUriAttributeDelegate = AttributeDelegate(UriValue)
val tagsAttributeDelegate = MultiValueSetAttributeDelegate<String>(CoreAttribute.Tags.spec)
val publicKeyAttributeDelegate = AttributeDelegate(PublicKeyValue)
val byteArrayAttributeDelegate = AttributeDelegate(ByteArrayValue)

@Suppress("UNCHECKED_CAST")
class MultiValueSetAttributeDelegate<T> (val attribute: Attribute) {
    // Returns a list so that the set can be ordered, but it will not contain duplicates
    operator fun getValue(thisRef: Entity, property: KProperty<*>): List<T> {
        return thisRef
            .getSetValues(property.name)
            .map { attribute.valueWrapper.unwrap(it) as T }
    }

    // Tales a list so that the set can be ordered, but it will not allow duplicates
    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: List<T>) {
        // Update any present values
        value.forEach { thisRef.setValue(property.name, attribute.valueWrapper.wrap(it as Any)) }

        // Delete any removed values
        thisRef
            .getSetValues(property.name)
            .toSet()
            .minus(value.map { attribute.valueWrapper.wrap(it as Any) }.toSet())
            .forEach { thisRef.deleteValue(property.name, null, it) }
    }
}
