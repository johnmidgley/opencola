package opencola.server.handlers

import io.opencola.util.nullOrElse
import io.opencola.model.Id
import io.opencola.storage.addressbook.AddressBookEntry
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
        val imageUri: String?
    ) {
        constructor(addressBookEntry: AddressBookEntry) : this(
            addressBookEntry.entityId.toString(),
            addressBookEntry.name,
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
    data class Action(val type: String, val id: String?, val value: String?) {
        constructor(type: ActionType, id: Id?, value: Any?) :
                this(type.name.lowercase(), id.nullOrElse { it.toString() }, value.nullOrElse { it.toString() })
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