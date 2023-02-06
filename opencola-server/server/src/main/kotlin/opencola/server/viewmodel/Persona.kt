package opencola.server.viewmodel

import io.opencola.security.encode
import kotlinx.serialization.Serializable
import io.opencola.model.Persona as ModelPersona

@Serializable
data class Persona(
    val id: String,
    val name: String,
    val publicKey: String,
    val address: String,
    val imageUri: String?,
    val isActive: Boolean,
) {
    constructor(modelPersona: ModelPersona) :
            this(
                modelPersona.entityId.toString(),
                modelPersona.name ?: "",
                modelPersona.publicKey?.encode() ?: "",
                modelPersona.uri.toString(),
                modelPersona.imageUri.toString(),
                modelPersona.getActive()
            )
}