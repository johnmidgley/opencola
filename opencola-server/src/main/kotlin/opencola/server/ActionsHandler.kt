package opencola.server

import io.ktor.application.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import opencola.core.config.Application
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.model.ResourceEntity
import opencola.core.storage.EntityStore
import org.kodein.di.instance
import java.net.URI

// TODO: Abstract out Handler with ApplicationCall, injector, logger, respond, etc.
class ActionsHandler(private val call: ApplicationCall) {
    val injector = Application.instance.injector
    val authority by injector.instance<Authority>()
    val entityStore by injector.instance<EntityStore>()

    @Serializable
    data class Actions(val trust: Float?, val like: Boolean?, val rating: Float?)

    suspend fun respond() {
        val stringUri = call.parameters["uri"] ?: throw IllegalArgumentException("No uri set")
        val entityId = Id.ofUri(URI(stringUri))
        val entity = entityStore.getEntity(authority, entityId) as? ResourceEntity

        if(entity != null){
            call.respond(Actions(entity.trust, entity.like, entity.rating))
        }
    }
}