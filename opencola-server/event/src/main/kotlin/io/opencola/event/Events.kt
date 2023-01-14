package io.opencola.event

// TODO: This should be generalized to support arbitrary events.
enum class Events {
    NodeStarted,
    NodeResume,
    NewTransaction,
    PeerNotification,
}