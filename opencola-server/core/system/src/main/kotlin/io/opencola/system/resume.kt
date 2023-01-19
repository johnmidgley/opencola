package io.opencola.system

import java.lang.Thread.sleep
import java.util.concurrent.Executors

private val logger = mu.KotlinLogging.logger("detectResume")
private val executorService = Executors.newSingleThreadExecutor()
private const val delayTimeMillis: Long = 10000L
private const val maxDelayTimeMillis: Long = 30000L

@Synchronized
fun detectResume(handler: () -> Unit) {
    logger.info("Launching resume detection")

    // Putting in thread, since it seems to be sensitive to co-routine implementation. Probably a better way to
    // ensure it's isolated.
    executorService.execute {
        try {
            while (true) {
                val timeBeforeDelayMillis = System.currentTimeMillis()
                sleep(delayTimeMillis)
                val actualDelayTimeMillis = System.currentTimeMillis() - timeBeforeDelayMillis

                if (actualDelayTimeMillis > maxDelayTimeMillis) {
                    handler()
                }
            }
        } catch (e: Throwable) {
            logger.error { "Error in resume detection: $e" }
        }
    }
}