/*
 * Copyright 2024-2026 OpenCola
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

package io.opencola.event.log

import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

class EventLogger(
    val name: String,
    val storagePath: Path,
    private val dateSplitFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd")
) : Closeable {
    companion object {
        // instance makes sure that nobody else will be writing to the same event file, which could cause corruption
        val instance: UUID = UUID.randomUUID()

        private val json = Json {
            encodeDefaults = false
            prettyPrint = false
        }

        private abstract class EventQueueItem
        private data class LogEntryItem(val eventLogEntry: EventLogEntry) : EventQueueItem()
        private class FlushLogItem(val semaphore: Semaphore) : EventQueueItem()
        private object CloseLogItem : EventQueueItem()
    }

    private val eventQueue = ArrayBlockingQueue<EventQueueItem>(1024)

    // Consider switching this to a co-routine based solution. The issue, though, is that this forces the client
    // to use a co-routine based solution as well, which may not be desirable.
    private val eventThread = thread {
        while (true) {
            val item = eventQueue.take()

            if(item is FlushLogItem) {
                item.semaphore.release()
                continue
            }

            if (item is CloseLogItem)
                break

            require(item is LogEntryItem)

            FileOutputStream(getLogfile(Date()), true).use { fos ->
                OutputStreamWriter(fos).use {
                    it.write(json.encodeToString(item.eventLogEntry))
                    it.write("\n")
                }
            }
        }
    }

    private fun getLogfile(date: Date): File {
        val dateString = dateSplitFormat.format(date)
        return storagePath.resolve("$name-$instance-$dateString.log").toFile()
    }

    fun log(name: String, parameters: Map<String, String> = emptyMap(), message: String? = null): EventLogEntry {
        require(name.isNotBlank()) { "name must not be blank" }
        EventLogEntry(name, parameters, message).let {
            eventQueue.add(LogEntryItem(it))
            return it
        }
    }

    fun flush() {
        Semaphore(0).let {
            eventQueue.add(FlushLogItem(it))
            it.acquire()
        }
    }

    override fun close() {
        eventQueue.add(CloseLogItem)
        eventThread.join()
    }
}