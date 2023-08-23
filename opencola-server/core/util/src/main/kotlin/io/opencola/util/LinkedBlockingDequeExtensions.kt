package io.opencola.util

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

fun <T> poll(q: LinkedBlockingDeque<T>, timeoutMilliSeconds: Long = 1000): T? {
    return q.poll(timeoutMilliSeconds, TimeUnit.MILLISECONDS)
}