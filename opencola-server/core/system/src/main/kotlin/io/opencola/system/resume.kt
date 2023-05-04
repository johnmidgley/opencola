package io.opencola.system

import java.lang.Thread.sleep
import java.util.concurrent.Executors

private val logger = mu.KotlinLogging.logger("detectResume")
private val executorService = Executors.newSingleThreadExecutor()

@Synchronized
fun detectResume(delayMillis: Long = 10000L, maxDelayMillis: Long = 30000L, handler: () -> Unit) {
    // Putting in thread, since it seems to be sensitive to co-routine implementation. Probably a better way to
    // ensure it's isolated.
    executorService.execute {
        try {
            while (true) {
                val timeBeforeDelayMillis = System.currentTimeMillis()
                sleep(delayMillis)
                val actualDelayTimeMillis = System.currentTimeMillis() - timeBeforeDelayMillis

                if (actualDelayTimeMillis > maxDelayMillis) {
                    handler()
                }
            }
        } catch (e: Throwable) {
            logger.error { "Error in resume detection: $e" }
        }
    }
}