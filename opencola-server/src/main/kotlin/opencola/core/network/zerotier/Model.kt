package opencola.core.network.zerotier

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Network(
    val id: String?,
    val type: String?,
    val clock: Long?,
    val config: NetworkConfig?,
    val description: String?,
    val rulesSource: String?,
    // val permissions: Permissions
    val ownerId: String?,
    val onlineMemberCount: Int?,
    val authorizedMemberCount: Int?,
    val totalMemberCount: Int?,
    val capabilitiesByName: Map<String,String?>?,
    val tagsByName: Map<String,String?>?,
)

@Serializable
data class NetworkConfig(
    val id: String?,
    val creationTime: Long?,
    val capabilities: List<Map<String, String>>?,
    // val dns: DNS
    val enableBroadcast: Boolean?,
    val ipAssignmentPools : List<IPRange>?,
    val lastModified: Long?,
    val mtu: Long?,
    val multicastLimit: Int?,
    val name: String?,
    val private: Boolean?,
    val routes: List<Route>?,
    val rules: List<Rule>?,
    // val tags: List<Map<String,String?>>?,
    val v4AssignMode: IPV4AssignMode,
    val v6AssignMode: IPV6AssignMode,
)

@Serializable
data class IPRange(
    val ipRangeStart: String,
    val ipRangeEnd: String,
)

@Serializable
data class Route(
    val target: String,
    val via: String?
)

@Serializable
data class Rule(
    val eitherType: Long?,
    val not: Boolean?,
    val or: Boolean?,
    val type: String?,
)

@Serializable
data class IPV4AssignMode(
    val zt: Boolean
)

@Serializable
data class IPV6AssignMode(
    @SerialName("6plane") val sixPlane: Boolean?,
    val rfc4193: Boolean?,
    val zt: Boolean?,
)

