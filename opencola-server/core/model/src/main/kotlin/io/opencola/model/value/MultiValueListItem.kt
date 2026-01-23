/*
 * Copyright 2024-2026 OpenCola
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

package io.opencola.model.value

import java.util.*

// TODO: Make value wrapper and remove toValue, keyOf and fromValue
class MultiValueListItem<T>(val key: UUID, value: T) : Value<T>(value) {
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other == null) return false
        if(other !is MultiValueListItem<*>) return false
        if(key != other.key) return false
        return this.asAnyValue() == other.asAnyValue()
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}