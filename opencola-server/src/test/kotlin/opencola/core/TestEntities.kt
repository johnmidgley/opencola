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
        URI("http://scratch.com/favicon"),
        "Scratch",
        "Cool online programming!",
        .75F,
        listOf<String>("programming", "educational").toSet(),
        true,
        .89F,
    )
}
