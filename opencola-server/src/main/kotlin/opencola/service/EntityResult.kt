package opencola.service

import kotlinx.serialization.Serializable
import opencola.core.model.Actions
import opencola.core.model.Authority
import opencola.core.model.Id

@Serializable
// TODO - Replace Search Result
data class EntityResult(
    val entityId: String,
    val dataId: String?,
    val summary: Summary,
    val activities: List<Activity>
) {
    // TODO: Remove dataId at top level - now part of activity, so any version can be accessed
    constructor(entityId: Id, dataId: Id?, summary: Summary, activities: List<Activity>) : this(
        entityId.toString(),
        dataId?.toString(),
        summary,
        activities
    )

    @Serializable
    data class Summary(val name: String?, val uri: String, val description: String?, val imageUri: String?)

    @Serializable
    data class Activity(
        val authorityId: String,
        val authorityName: String,
        val host: String,
        val dataId: String?,
        val epochSecond: Long,
        val actions: Actions
    ) {
        constructor(authority: Authority, dataId: Id?, epochSecond: Long, actions: Actions) :
                this(authority.authorityId.toString(), authority.name!!, authority.uri!!.authority ?: "", dataId?.toString(), epochSecond, actions)
    }
}