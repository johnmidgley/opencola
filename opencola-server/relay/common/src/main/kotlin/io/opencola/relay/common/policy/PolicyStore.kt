/*
 * Copyright 2024 OpenCola
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

import io.opencola.model.Id

/**
 * Interface for managing policies that define what users can do on the relay server. The rootId is defined in
 * configuration and has all privileges.
 */
interface PolicyStore {
    val rootId: Id

    fun setPolicy(authorityId: Id, policy: Policy)
    fun getPolicy(authorityId: Id, policyName: String): Policy?
    fun getPolicies(authorityId: Id): Sequence<Policy>
    fun removePolicy(authorityId: Id, policyName: String)

    fun setUserPolicy(authorityId: Id, userId: Id, policyName: String)
    fun getUserPolicy(authorityId: Id, userId: Id): Policy?
    fun getUserPolicies(authorityId: Id): Sequence<Pair<Id, String>>
    fun removeUserPolicy(authorityId: Id, userId: Id)
}