package io.opencola.relay.common.policy

import kotlinx.serialization.Serializable

@Serializable
data class AdminPolicy(val canEditPolicies: Boolean = false, val canEditUsers: Boolean = false)

@Serializable
data class ConnectionPolicy(val canConnect: Boolean = true)

@Serializable
data class MessagePolicy(val maxMessageSize: Int = 1024 * 1024 * 50)

@Serializable
data class StoragePolicy(val maxStoredBytes: Int = 1024 * 1024 * 50)

@Serializable
data class Policy(
    val adminPolicy: AdminPolicy = AdminPolicy(),
    val connectionPolicy: ConnectionPolicy = ConnectionPolicy(),
    val messagePolicy: MessagePolicy = MessagePolicy(),
    val storagePolicy: StoragePolicy = StoragePolicy()
)