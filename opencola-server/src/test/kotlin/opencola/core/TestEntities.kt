import opencola.core.security.generateKeyPair
import opencola.core.security.privateKeyFromBytes
import opencola.core.security.publicKeyFromBytes
import opencola.core.model.Id
import opencola.core.extensions.hexStringToByteArray
import opencola.core.model.ActorEntity
import opencola.core.model.Authority
import java.net.URI
import java.security.KeyPair

val authorityPublicKey = publicKeyFromBytes("3059301306072a8648ce3d020106082a8648ce3d030107034200043afa5d5e418d40dcce131c15cc0338e2be043584b168f3820ddc120259641973edff721756948b0bb8833b486fbde224b5e4987432383f79c3e013ebc40f0dc3".hexStringToByteArray())
val authorityPrivateKey = privateKeyFromBytes("3041020100301306072a8648ce3d020106082a8648ce3d03010704273025020101042058d9eb4708471a6189dcd6a5e37a724c158be8e820d90a1050f7a1d5876acf58".hexStringToByteArray())

fun getActorEntity(subjectId: Id) : ActorEntity {
    return ActorEntity(
        subjectId,
        generateKeyPair().public,
        URI("http://scatch.com"),
        URI("http://scratch.com/favicon"),
        "Scratch",
        "Cool online programming!",
        .75F,
        listOf<String>("programming", "educational").toSet(),
        true,
        .89F,
    )
}

fun getAuthorityKeyPair(): KeyPair {
    return KeyPair(authorityPublicKey, authorityPrivateKey)
}

fun getAuthority() : Authority {
    return Authority(authorityPublicKey)
}