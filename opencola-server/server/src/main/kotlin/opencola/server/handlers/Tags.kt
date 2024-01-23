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

import io.opencola.event.bus.EventBus
import io.opencola.model.Id
import io.opencola.model.RawEntity
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentAddressedFileStore
import kotlinx.serialization.Serializable

fun getTags(tagsString: String?): List<String> {
    return tagsString?.let { tags ->
        tags
            .split(" ")
            .filter { it.isNotBlank() }
    }?.toList() ?: emptyList()
}

@Serializable
data class TagsPayload(val value: String?) {
    fun getTags(): List<String> {
        return getTags(value)
    }
}

fun tagEntity(
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
    context: Context,
    persona: PersonaAddressBookEntry,
    entityId: Id,
    tagsPayload: TagsPayload
): EntityResult? {
    val personaId = persona.personaId
    val entity = entityStore.getEntity(personaId, entityId) ?: RawEntity(personaId, entityId)
    entity.tags = tagsPayload.getTags()
    entityStore.updateEntities(entity)
    return getEntityResult(entityStore, addressBook, eventBus, fileStore, context, personaId, entityId)
}