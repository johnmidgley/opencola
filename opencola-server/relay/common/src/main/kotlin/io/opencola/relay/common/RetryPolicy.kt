/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.relay.common

import java.lang.Long.min
import java.time.Duration

/**
 * Policies defining how long to wait before retrying an operation.
 */

// Not used on production
val retryContinuous: (Int) -> Long = { 0 }

// Not used in production
fun retryConstantInterval(intervalInMilliseconds: Long): (Int) -> Long {
    return { intervalInMilliseconds }
}

fun retryExponentialBackoff(
    baseIntervalInMilliseconds: Long = 1000,
    maxIntervalInMilliseconds: Long = Duration.ofHours(1).seconds * 1000
)
        : (Int) -> Long {
    return { numFailures ->
        min(baseIntervalInMilliseconds * 1L.shl(numFailures - 1), maxIntervalInMilliseconds)
    }
}
