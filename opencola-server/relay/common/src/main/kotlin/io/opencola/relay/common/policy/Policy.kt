package io.opencola.relay.common.policy

import kotlinx.serialization.Serializable

@Serializable
data class AdminPolicy(
    val isAdmin: Boolean = false,
    val canEditPolicies: Boolean = false,
    val canEditUserPolicies: Boolean = false
)

@Serializable
data class ConnectionPolicy(val canConnect: Boolean = true)

@Serializable
data class MessagePolicy(val maxPayloadSize: Long = 1024 * 1024 * 50)

@Serializable
data class StoragePolicy(val maxStoredBytes: Long = 1024 * 1024 * 50)

@Serializable
data class Policy(
    val name: String,
    val adminPolicy: AdminPolicy = AdminPolicy(),
    val connectionPolicy: ConnectionPolicy = ConnectionPolicy(),
    val messagePolicy: MessagePolicy = MessagePolicy(),
    val storagePolicy: StoragePolicy = StoragePolicy()
)