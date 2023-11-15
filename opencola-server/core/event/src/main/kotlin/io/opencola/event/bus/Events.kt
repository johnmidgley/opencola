package io.opencola.event.bus

// TODO: This should be generalized to support arbitrary events.
enum class Events {
    NodeStarted,
    NodeResume,
    NewTransaction, // Not used anymore - could be removed
    PeerNotification,
    NoPendingNetworkMessages,
    DataMissing,
}