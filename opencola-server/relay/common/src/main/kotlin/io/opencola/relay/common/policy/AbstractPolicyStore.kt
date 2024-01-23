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

abstract class AbstractPolicyStore(final override val rootId: Id, protected var defaultPolicy: Policy?) : PolicyStore {
    private fun expectUserPolicy(userId: Id): Policy {
        return getUserPolicy(rootId, userId) ?: throw IllegalStateException("Missing policy for user $userId")
    }

    protected fun authorizeReadPolicy(authorityId: Id) {
        if (authorityId != rootId && !expectUserPolicy(authorityId).adminPolicy.canEditPolicies) {
            throw IllegalStateException("User $authorityId cannot read policies")
        }
    }

    protected fun authorizeEditPolicies(authorityId: Id) {
        if (authorityId != rootId && !expectUserPolicy(authorityId).adminPolicy.canEditPolicies) {
            throw IllegalStateException("User $authorityId cannot edit policies")
        }
    }

    protected fun authorizeReadUserPolicy(authorityId: Id, userId: Id) {
        if (authorityId != rootId && authorityId != userId && !expectUserPolicy(authorityId).adminPolicy.canEditUserPolicies) {
            throw IllegalStateException("User $authorityId cannot read policy of user $userId")
        }
    }

    protected fun authorizeEditUserPolicies(authorityId: Id) {
        if (authorityId != rootId && !expectUserPolicy(authorityId).adminPolicy.canEditUserPolicies) {
            throw IllegalStateException("User $authorityId cannot edit user policies")
        }
    }
}