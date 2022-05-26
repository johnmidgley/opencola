package opencola.core.model

import java.security.PublicKey

data class Peer(val id: Id, val publicKey: PublicKey, val name: String, val host: String, val active: Boolean)