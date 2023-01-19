package io.opencola.util

import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

fun ExecutorService.shutdownWithTimout(timeoutInMilliseconds: Long) {
    try {
        if (!this.awaitTermination(timeoutInMilliseconds, TimeUnit.MILLISECONDS)) {
            this.shutdownNow()
        }
    } catch (e: InterruptedException) {
        this.shutdownNow()
    }
}