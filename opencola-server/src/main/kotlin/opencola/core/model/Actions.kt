package opencola.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Actions(val save: Boolean? = null, val trust: Float? = null, val like: Boolean? = null, val rating: Float? = null)