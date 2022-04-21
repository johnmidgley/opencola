package opencola.core.model

import java.security.PublicKey

class Peer(val id: Id, val publicKey: PublicKey, val name: String, val host: String, val active: Boolean)