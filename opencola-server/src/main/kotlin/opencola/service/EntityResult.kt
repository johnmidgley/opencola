package opencola.service

import kotlinx.serialization.Serializable
import opencola.core.model.Actions
import opencola.core.model.Id

@Serializable
// TODO - Replace Search Result
data class EntityResult(val entityId: String, val summary: Summary, val activities: List<Activity>){
    constructor(entityId: Id, summary: Summary, activities: List<Activity>) : this(entityId.toString(), summary, activities)

    @Serializable
    data class Summary(val name: String?, val uri: String, val description: String?)

    @Serializable
    data class Activity(val authorityId: String, val authorityName: String, val epochSecond: Long, val actions: Actions){
        constructor(authorityId: Id, authorityName: String, epochSecond: Long, actions: Actions) : this(authorityId.toString(), authorityName, epochSecond, actions)
    }
}