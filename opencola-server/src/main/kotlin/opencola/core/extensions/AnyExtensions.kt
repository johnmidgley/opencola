package opencola.core.extensions

inline fun <T : Any, R> T?.ifNotNullOrElse(ifNotNullLambda: (T) -> R, elseLambda: () -> R)
        = let { if(it == null) elseLambda() else ifNotNullLambda(it) }

inline fun <T : Any, R> T?.nullOrElse(ifNotNullLambda: (T) -> R)
        = let { if(it == null) null else ifNotNullLambda(it) }