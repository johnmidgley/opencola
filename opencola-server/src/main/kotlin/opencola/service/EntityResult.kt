package opencola.service

import io.opencola.core.extensions.nullOrElse
import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

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
    data class Summary(val name: String?, val uri: String?, val description: String?, val imageUri: String?, val postedBy: String?, val postedByImageUri: String?)

    enum class ActionType() {
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
        // TODO: Make Authority(id, name, host)
        val authorityId: String,
        val authorityName: String,
        val host: String,
        val dateTime: String,
        val actions: List<Action>,
    ) {
        constructor(authority: Authority, epochSecond: Long, actions: List<Action>) :
                this(
                    authority.entityId.toString(),
                    authority.name!!,
                    authority.uri!!.authority ?: "",
                    epochSecondToDateString(epochSecond),
                    actions)

        fun getEpochSecond() : Long {
            return dateFormat.parse(dateTime).time / 1000
        }
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss aa").also {
            it.timeZone =  Calendar.getInstance().timeZone
        }

        fun epochSecondToDateString(epochSecond: Long) : String {
            val date = Date(epochSecond * 1000)
            return dateFormat.format(date)
        }
    }
}