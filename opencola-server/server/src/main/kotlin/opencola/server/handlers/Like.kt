package opencola.server.handlers

import io.opencola.event.bus.EventBus
import io.opencola.model.Id
import io.opencola.model.RawEntity
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentAddressedFileStore
import kotlinx.serialization.Serializable

@Serializable
data class LikePayload(
    val value: Boolean?
)

fun likeEntity(
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
    context: Context,
    persona: PersonaAddressBookEntry,
    entityId: Id,
    likePayload: LikePayload
): EntityResult? {
    val personaId = persona.personaId
    val entity = entityStore.getEntity(personaId, entityId) ?: RawEntity(personaId, entityId)
    entity.like = likePayload.value
    entityStore.updateEntities(entity)
    return getEntityResult(entityStore, addressBook, eventBus, fileStore, context, personaId, entityId)
}