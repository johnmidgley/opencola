package opencola.core.extensions

import opencola.core.model.Fact
import opencola.core.model.Operation

fun Iterable<Fact>.currentFacts() : List<Fact> {
    return this
        .groupBy { it.attribute }
        .mapNotNull { (_, facts) ->
            facts
                .lastOrNull()
                .nullOrElse { if (it.operation != Operation.Retract) it else null }
        }
}
