package opencola.core

import io.opencola.security.generateKeyPair
import io.opencola.model.Id
import io.opencola.model.Authority
import java.net.URI

fun getAuthorityEntity(subjectId: Id) : Authority {
    return Authority(
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