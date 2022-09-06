package io.opencola.relay.common

import java.lang.Long.min
import java.time.Duration

val retryContinuous: (Int) -> Long = { 0 }

fun retryConstantInterval(intervalInMilliseconds: Long): (Int) -> Long {
    return { intervalInMilliseconds }
}

fun retryExponentialBackoff(baseIntervalInMilliseconds: Long = 1000,
                            maxIntervalInMilliseconds: Long = Duration.ofHours(1).seconds * 1000)
: (Int) -> Long {
    return { numFailures ->
        min(baseIntervalInMilliseconds * 1L.shl(numFailures - 1), maxIntervalInMilliseconds)
    }
}
