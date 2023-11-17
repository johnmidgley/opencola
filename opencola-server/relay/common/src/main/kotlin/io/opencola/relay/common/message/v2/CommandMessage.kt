package io.opencola.relay.common.message.v2

import io.opencola.model.Id
import io.opencola.model.IdAsStringSerializer
import io.opencola.relay.common.message.v2.store.Usage
import io.opencola.relay.common.policy.Policy
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.nio.charset.Charset
import java.util.*

enum class Status {
    SUCCESS,
    FAILURE
}

enum class State {
    COMPLETE,
    PENDING
}

@Serializable
sealed class CommandMessage {
    abstract val id: String

    companion object {
        val json = Json { serializersModule = SerializersModule { contextual(Id::class, IdAsStringSerializer) } }

        fun encode(command: CommandMessage): ByteArray {
            return json.encodeToString(command).toByteArray()
        }

        fun decode(payload: ByteArray): CommandMessage {
            return json.decodeFromString<CommandMessage>(payload.toString(Charset.defaultCharset()))
        }
    }

    fun encode(): ByteArray {
        return encode(this)
    }
}

@Serializable
@SerialName("CommandResponse")
data class CommandResponse(override val id: String, val status: Status, val state: State, val message: String? = null) : CommandMessage()

@Serializable
@SerialName("SetPolicyCommand")
data class SetPolicyCommand(val policy: Policy, override val id: String = UUID.randomUUID().toString()) : CommandMessage()

@Serializable
@SerialName("GetPolicyCommand")
data class GetPolicyCommand(val name: String, override val id: String = UUID.randomUUID().toString()) : CommandMessage()

@Serializable
@SerialName("GetPolicyResponse")
data class GetPolicyResponse(override val id: String, val policy: Policy? = null) : CommandMessage()

@Serializable
@SerialName("GetPoliciesCommand")
data class GetPoliciesCommand(override val id: String = UUID.randomUUID().toString()) : CommandMessage()

@Serializable
@SerialName("GetPoliciesResponse")
data class GetPoliciesResponse(override val id: String, val policies: List<Policy> = emptyList()) : CommandMessage()

@Serializable
@SerialName("SetUserPolicyCommand")
data class SetUserPolicyCommand(@Contextual val userId: Id, val policyName: String, override val id: String = UUID.randomUUID().toString()) : CommandMessage()

@Serializable
@SerialName("GetUserPolicyResponse")
data class GetUserPolicyResponse(override val id: String, val policy: Policy? = null) : CommandMessage()

@Serializable
@SerialName("GetUserPolicyCommand")
data class GetUserPolicyCommand(@Contextual val userId: Id, override val id: String = UUID.randomUUID().toString()) : CommandMessage()

@Serializable
@SerialName("GetUserPoliciesCommand")
data class GetUserPoliciesCommand(override val id: String = UUID.randomUUID().toString()) : CommandMessage()

@Serializable
@SerialName("GetUserPoliciesResponse")
data class GetUserPoliciesResponse(override val id: String, val policies: List<Pair<@Contextual Id, String>> = emptyList()) : CommandMessage()

@Serializable
@SerialName("RemoveUserMessagesCommand")
data class RemoveUserMessagesCommand(@Contextual val userId: Id, override val id: String = UUID.randomUUID().toString()) : CommandMessage()

@Serializable
@SerialName("RemoveMessagesByAgeCommand")
data class RemoveMessagesByAgeCommand(val maxAgeMilliseconds: Long, override val id: String = UUID.randomUUID().toString()) : CommandMessage()

@Serializable
@SerialName("GetMessageUsageCommand")
data class GetMessageUsageCommand(override val id: String = UUID.randomUUID().toString()) : CommandMessage()

@Serializable
@SerialName("GetMessageUsageResponse")
data class GetMessageUsageResponse(override val id: String, val usages: List<Usage>) : CommandMessage()

// TODO: Add GetUserMessagesCommand / Response