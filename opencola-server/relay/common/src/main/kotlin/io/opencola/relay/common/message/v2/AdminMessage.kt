package io.opencola.relay.common.message.v2

import io.opencola.model.Id
import io.opencola.model.IdAsStringSerializer
import io.opencola.relay.common.message.v2.store.StoredMessage
import io.opencola.relay.common.message.v2.store.Usage
import io.opencola.relay.common.policy.Policy
import io.opencola.serialization.ByteArrayAsStringSerializer
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
sealed class AdminMessage {
    abstract val id: String

    companion object {
        private val json =
            Json {
                serializersModule = SerializersModule {
                    contextual(Id::class, IdAsStringSerializer)
                    contextual(ByteArray::class, ByteArrayAsStringSerializer)
                }
            }

        fun encode(adminMessage: AdminMessage): ByteArray {
            return json.encodeToString(adminMessage).toByteArray()
        }

        fun decode(payload: ByteArray): AdminMessage {
            return json.decodeFromString<AdminMessage>(payload.toString(Charset.defaultCharset()))
        }
    }

    fun encode(): ByteArray {
        return encode(this)
    }
}

@Serializable
@SerialName("CommandResponse")
data class CommandResponse(override val id: String, val status: Status, val state: State, val message: String? = null) :
    AdminMessage()

@Serializable
@SerialName("SetPolicyCommand")
data class SetPolicyCommand(val policy: Policy, override val id: String = UUID.randomUUID().toString()) : AdminMessage()

@Serializable
@SerialName("GetPolicyCommand")
data class GetPolicyCommand(val name: String, override val id: String = UUID.randomUUID().toString()) : AdminMessage()

@Serializable
@SerialName("GetPolicyResponse")
data class GetPolicyResponse(override val id: String, val policy: Policy? = null) : AdminMessage()

@Serializable
@SerialName("RemovePolicyCommand")
data class RemovePolicyCommand(val name: String, override val id: String = UUID.randomUUID().toString()) :
    AdminMessage()

@Serializable
@SerialName("GetPoliciesCommand")
data class GetPoliciesCommand(override val id: String = UUID.randomUUID().toString()) : AdminMessage()

@Serializable
@SerialName("GetPoliciesResponse")
data class GetPoliciesResponse(override val id: String, val policies: List<Policy> = emptyList()) : AdminMessage()

@Serializable
@SerialName("SetUserPolicyCommand")
data class SetUserPolicyCommand(
    @Contextual val userId: Id,
    val policyName: String,
    override val id: String = UUID.randomUUID().toString()
) : AdminMessage()

@Serializable
@SerialName("GetUserPolicyCommand")
data class GetUserPolicyCommand(@Contextual val userId: Id, override val id: String = UUID.randomUUID().toString()) :
    AdminMessage()

@Serializable
@SerialName("GetUserPolicyResponse")
data class GetUserPolicyResponse(override val id: String, val policy: Policy? = null) : AdminMessage()

@Serializable
@SerialName("GetUserPoliciesCommand")
data class GetUserPoliciesCommand(override val id: String = UUID.randomUUID().toString()) : AdminMessage()

@Serializable
@SerialName("GetUserPoliciesResponse")
data class GetUserPoliciesResponse(
    override val id: String,
    val policies: List<Pair<@Contextual Id, String>> = emptyList()
) : AdminMessage()

@Serializable
@SerialName("RemoveUserPolicyCommand")
data class RemoveUserPolicyCommand(@Contextual val userId: Id, override val id: String = UUID.randomUUID().toString()) :
    AdminMessage()

@Serializable
@SerialName("GetConnectionsCommand")
data class GetConnectionsCommand(override val id: String = UUID.randomUUID().toString()) : AdminMessage()

@Serializable
data class ConnectionInfo(@Contextual val id: Id, val address: String, val connectTimeMilliseconds: Long)

@Serializable
@SerialName("GetConnectionsResponse")
data class GetConnectionsResponse(override val id: String, val connections: List<ConnectionInfo> = emptyList()) :
    AdminMessage()

@Serializable
@SerialName("GetMessagesCommand")
data class GetMessagesCommand(override val id: String = UUID.randomUUID().toString()) : AdminMessage()

@Serializable
data class MessageInfo(
    @Contextual val from: Id,
    @Contextual val to: Id,
    val sizeInBytes: Int,
    val timeMilliseconds: Long
) {
    constructor(storedMessage: StoredMessage) : this(
        storedMessage.header.from,
        storedMessage.header.to,
        storedMessage.body.bytes.count(),
        storedMessage.header.timeMilliseconds
    )
}

@Serializable
@SerialName("GetMessagesResponse")
data class GetMessagesResponse(override val id: String, val messages: List<MessageInfo> = emptyList()) : AdminMessage()

@Serializable
@SerialName("RemoveUserMessagesCommand")
data class RemoveUserMessagesCommand(
    @Contextual val userId: Id,
    override val id: String = UUID.randomUUID().toString()
) : AdminMessage()

@Serializable
@SerialName("RemoveMessagesByAgeCommand")
data class RemoveMessagesByAgeCommand(
    val maxAgeMilliseconds: Long,
    override val id: String = UUID.randomUUID().toString()
) : AdminMessage()

@Serializable
@SerialName("GetMessageUsageCommand")
data class GetMessageUsageCommand(override val id: String = UUID.randomUUID().toString()) : AdminMessage()

@Serializable
@SerialName("GetMessageUsageResponse")
data class GetMessageUsageResponse(override val id: String, val usages: List<Usage>) : AdminMessage()

@Serializable
@SerialName("ExecCommand")
data class ExecCommand(
    val workingDir: String,
    val command: String,
    override val id: String = UUID.randomUUID().toString()
) : AdminMessage()

@Serializable
@SerialName("ExecCommandResponse")
data class ExecCommandResponse(
    override val id: String,
    val workingDir: String,
    val stdout: String,
    val stderr: String
) : AdminMessage()

@Serializable
@SerialName("GetFileCommand")
data class GetFileCommand(val path: String, override val id: String = UUID.randomUUID().toString()) : AdminMessage()

@Serializable
@SerialName("GetFileBlockResponse")
data class GetFileBlockResponse(
    override val id: String,
    @Contextual
    val block: ByteArray
) : AdminMessage() {
    override fun toString(): String {
        return "GetFileBlockResponse(id='$id', block=${block.size} bytes)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GetFileBlockResponse

        if (id != other.id) return false
        if (!block.contentEquals(other.block)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + block.contentHashCode()
        return result
    }
}
