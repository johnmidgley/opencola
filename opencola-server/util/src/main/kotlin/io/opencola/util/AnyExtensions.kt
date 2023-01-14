package io.opencola.util

inline fun <T : Any, R> T?.ifNotNullOrElse(ifNotNullLambda: (T) -> R, elseLambda: () -> R)
        = let { if(it == null) elseLambda() else ifNotNullLambda(it) }

inline fun <T : Any, R> T?.ifNullOrElse(ifNullValue: R, elseLambda: (T) -> R)
        = let { if(it == null) ifNullValue else elseLambda(it) }

// TODO: This is not needed - just use ?.let { it -> }
inline fun <T : Any, R> T?.nullOrElse(ifNotNullLambda: (T) -> R)
        = let { if(it == null) null else ifNotNullLambda(it) }
