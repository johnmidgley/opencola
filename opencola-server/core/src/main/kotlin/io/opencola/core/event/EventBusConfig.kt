package io.opencola.core.event

// TODO: Add config layers, that allow for override. Use push / pop
// https://github.com/sksamuel/hoplite
data class EventBusConfig(val name: String = "event-bus", val maxAttempts: Int = 3)