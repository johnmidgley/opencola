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