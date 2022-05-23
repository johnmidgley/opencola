package opencola.core

import opencola.core.security.generateKeyPair
import opencola.core.model.Id
import opencola.core.model.ActorEntity
import java.net.URI

fun getActorEntity(subjectId: Id) : ActorEntity {
    return ActorEntity(
        subjectId,
        generateKeyPair().public,
        URI("http://scatch.com"),
        "Scratch",
        "Cool online programming!",
        "",
        URI("http://scratch.com/favicon"),
        .89F,
        true,
        .75F,
        setOf("programming", "educational"),
    )
}
