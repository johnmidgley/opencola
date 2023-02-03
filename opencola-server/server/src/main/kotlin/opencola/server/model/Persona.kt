package opencola.server.model

import io.opencola.security.encode
import kotlinx.serialization.Serializable
import io.opencola.model.Persona as PersonaModel

@Serializable
data class Persona(
    val id: String,
    val name: String,
    val publicKey: String,
    val address: String,
    val imageUri: String?,
    val isActive: Boolean,
) {
    constructor(persona: PersonaModel) :
            this(
                persona.entityId.toString(),
                persona.name ?: "",
                persona.publicKey?.encode() ?: "",
                persona.uri.toString(),
                persona.imageUri.toString(),
                persona.getActive()
            )
}