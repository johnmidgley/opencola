package opencola.service

import kotlinx.serialization.Serializable
import opencola.core.extensions.nullOrElse
import opencola.core.model.Authority
import opencola.core.model.Id

@Serializable
// TODO - Replace Search Result
data class EntityResult(
    val entityId: String,
    val summary: Summary,
    val activities: List<Activity>
) {
    // TODO: Remove dataId at top level - now part of activity, so any version can be accessed
    constructor(entityId: Id, summary: Summary, activities: List<Activity>) : this(
        entityId.toString(),
        summary,
        activities
    )

    @Serializable
    data class Summary(val name: String?, val uri: String, val description: String?, val imageUri: String?)

    enum class ActionType(){
        Save,
        Trust,
        Like,
        Rate,
        Tag,
        Comment,
    }

    @Serializable
    data class Action(val type: String, val id: String?, val value: String?){
        constructor(type: ActionType, id: Id?, value: Any?) :
                this(type.name.lowercase(), id.nullOrElse { it.toString() }, value.nullOrElse { it.toString() })
    }


    @Serializable
    data class Activity(
        val authorityId: String,
        val authorityName: String,
        val host: String,
        val epochSecond: Long,
        val actions: List<Action>,
    ) {
        constructor(authority: Authority, epochSecond: Long, actions: List<Action>) :
                this(authority.authorityId.toString(), authority.name!!, authority.uri!!.authority ?: "", epochSecond, actions)
    }
}