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

package opencola.server.handlers

import io.opencola.model.Id
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import kotlinx.serialization.Serializable

@Serializable
// TODO - Replace Search Result
data class EntityResult(
    val entityId: String,
    val personaId: String,
    val summary: Summary,
    val activities: List<Activity>
) {
    // TODO: Remove dataId at top level - now part of activity, so any version can be accessed
    constructor(entityId: Id, personaId: Id, summary: Summary, activities: List<Activity>) : this(
        entityId.toString(),
        personaId.toString(),
        summary,
        activities
    )

    @Serializable
    data class Authority(
        val id: String,
        val name: String,
        val isPersona: Boolean,
        val imageUri: String? = null,
    ) {
        constructor(addressBookEntry: AddressBookEntry) : this(
            addressBookEntry.entityId.toString(),
            addressBookEntry.name,
            addressBookEntry is PersonaAddressBookEntry,
            addressBookEntry.imageUri?.toString()
        )
    }

    // TODO: Add a typed constructor to this and other model objects
    @Serializable
    data class Summary(
        val name: String?,
        val uri: String?,
        val description: String?,
        val imageUri: String?,
        val originDistance: Int?,
        val postedBy: Authority?,
    )

    enum class ActionType() {
        Save,
        Trust,
        Like,
        Rate,
        Tag,
        Comment,
        Attach,
    }

    @Serializable
    data class Action(val type: String, val id: String? = null, val value: String? = null, val parentId: String? = null) {
        constructor(type: ActionType, id: Id?, value: Any?, parentId: Id? = null) :
                this(type.name.lowercase(), id?.toString(), value?.toString(), parentId?.toString())
    }

    @Serializable
    data class Activity(
        // TODO: Make Authority(id, name, host)
        val authorityId: String,
        val authorityName: String,
        val host: String,
        val epochSecond: Long,
        val actions: List<Action>,
    ) {
        constructor(addressBookEntry: AddressBookEntry, epochSecond: Long, actions: List<Action>) :
                this(
                    addressBookEntry.entityId.toString(),
                    addressBookEntry.name,
                    addressBookEntry.address.authority ?: "",
                    epochSecond,
                    actions
                )
    }
}