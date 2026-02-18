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

package io.opencola.relay.common.policy

import kotlinx.serialization.Serializable

/**
 * Policies are associated with users (by Id) and define what each user is allowed to do on the relay server.
 */

@Serializable
data class AdminPolicy(
    val isAdmin: Boolean = false,
    val canEditPolicies: Boolean = false,
    val canEditUserPolicies: Boolean = false
)

@Serializable
data class ConnectionPolicy(val canConnect: Boolean = true)

@Serializable
data class MessagePolicy(val maxPayloadBytes: Long = 1024 * 1024 * 50)

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