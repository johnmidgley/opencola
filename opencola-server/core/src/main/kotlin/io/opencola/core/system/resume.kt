package io.opencola.core.system

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
                logger.info { "Waiting $delayTimeMillis ms" }
                val timeBeforeDelayMillis = System.currentTimeMillis()
                sleep(delayTimeMillis)
                val actualDelayTimeMillis = System.currentTimeMillis() - timeBeforeDelayMillis
                logger.info { "Actual delay $actualDelayTimeMillis ms" }

                if (actualDelayTimeMillis > maxDelayTimeMillis) {
                    logger.info { "Resume detected (ActualDelay:$actualDelayTimeMillis ms MaxDelay: $maxDelayTimeMillis)" }
                    handler()
                }
            }
        } catch (e: Throwable) {
            logger.error { "Error in resume detection: $e" }
        }
    }
}