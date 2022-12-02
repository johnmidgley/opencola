package io.opencola.core.system

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

suspend fun detectResume(handler: () -> Unit): Job = coroutineScope {
    var lastCheckTimeMillis = System.currentTimeMillis()

    launch {
        while (true) {
            delay(5000)
            val currentTimeMillis = System.currentTimeMillis()

            if (currentTimeMillis - lastCheckTimeMillis > 10000) {
                handler()
            }

            lastCheckTimeMillis = currentTimeMillis
        }
    }
}