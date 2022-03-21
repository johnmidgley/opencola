package opencola.service

import kotlinx.serialization.Serializable
import opencola.core.model.Actions
import opencola.core.model.Id

@Serializable
// TODO - Replace Search Result
data class EntityResult(val entityId: Id, val summary: Summary, val activities: List<Activity>){
    @Serializable
    data class Summary(val name: String?, val uri: String, val description: String?)

    @Serializable
    data class Activity(val authorityId: Id, val epochSecond: Long, val actions: Actions)
}