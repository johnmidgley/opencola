/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.model

import io.opencola.model.value.*
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST")
class AttributeDelegate<T>(val valueWrapper: ValueWrapper<T>, val resettable: Boolean = true) {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): T? {
        return thisRef.getValue(property.name)?.let { valueWrapper.unwrap(it as Value<T>) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: T?) {
        if (!resettable && getValue(thisRef, property) != null)
            throw IllegalStateException("Attempt to reset a non resettable property: ${property.name}")

        thisRef.setValue(property.name, value?.let { valueWrapper.wrap(it) as Value<Any> })
    }
}

@Suppress("UNCHECKED_CAST")
class StringAttributeDelegate(val resettable: Boolean = true, val convertBlankToNull: Boolean = true) {
    operator fun getValue(thisRef: Entity, property: KProperty<*>): String? {
        return thisRef.getValue(property.name)?.let { StringValue.unwrap(it as Value<String>) }
    }

    operator fun setValue(thisRef: Entity, property: KProperty<*>, value: String?) {
        if (!resettable && getValue(thisRef, property) != null)
            throw IllegalStateException("Attempt to reset a non resettable property: ${property.name}")

        val valueToSet = if (value == null || (convertBlankToNull && value.isBlank())) null else value
        thisRef.setValue(property.name, valueToSet?.let { StringValue.wrap(it) as Value<Any> })
    }
}

// TODO - validate all fields (i.e. ratings should be between 0 and 1.0)
val nonResettableIdAttributeDelegate = AttributeDelegate(IdValue, false)
val booleanAttributeDelegate = AttributeDelegate(BooleanValue)
val intAttributeDelegate = AttributeDelegate(IntValue)
val floatAttributeDelegate = AttributeDelegate(FloatValue)
val nonResettableStringValueDelegate = AttributeDelegate(StringValue, false)
val stringAttributeDelegate = StringAttributeDelegate()
val nonResettableUriAttributeDelegate = AttributeDelegate(UriValue, false)
val uriAttributeDelegate = AttributeDelegate(UriValue)
val imageUriAttributeDelegate = AttributeDelegate(UriValue)
val tagsAttributeDelegate = MultiValueSetAttributeDelegate<String>(CoreAttribute.Tags.spec)
val publicKeyAttributeDelegate = AttributeDelegate(PublicKeyValue)
val byteArrayAttributeDelegate = AttributeDelegate(ByteArrayValue)

@Suppress("UNCHECKED_CAST")
class MultiValueSetAttributeDelegate<T>(val attribute: Attribute) {
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
