package opencola.core.network.zerotier

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Model was build off https://docs.zerotier.com/openapi/centralv1.json
// Yes - an attempt was made to use openapi tools to generate a  client, but it did not produce valid code
// (Java or Kotlin) and modifying the generated Java client to compile still resulted in other errors during use,
// so it did not seem reliable enough, and there's too much obfuscation for a sample class to json mapping.

@Serializable
data class Network(
    val id: String? = null,
    val type: String? = null,
    val clock: Long? = null,
    val config: NetworkConfig? = null,
    val description: String? = null,
    val rulesSource: String? = null,
    // val permissions: Permissions
    val ownerId: String? = null,
    val onlineMemberCount: Int? = null,
    val authorizedMemberCount: Int? = null,
    val totalMemberCount: Int? = null,
    val capabilitiesByName: Map<String, String?>? = null,
    val tagsByName: Map<String, String?>? = null,
) {
    companion object Factory {
        fun forCreate(config: NetworkConfig,
                      description: String,
                      rulesSource: String? = null) : Network {
            return Network(config = config, description = description, rulesSource = rulesSource)
        }
    }
}

@Serializable
data class NetworkConfig(
    val id: String? = null,
    val creationTime: Long? = null,
    val capabilities: List<Map<String, String>>? = null,
    // val dns: DNS
    val enableBroadcast: Boolean? = null,
    val ipAssignmentPools : List<IPRange>? = null,
    val lastModified: Long? = null,
    val mtu: Long? = null,
    val multicastLimit: Int? = null,
    val name: String? = null,
    val private: Boolean? = null,
    val routes: List<Route>? = null,
    val rules: List<Rule>? = null,
    // val tags: List<Map<String,String?>>? = null,
    val v4AssignMode: IPV4AssignMode? = null,
    val v6AssignMode: IPV6AssignMode? = null,
) {
    companion object Factory {
        fun forCreate(
            enableBroadcast: Boolean? = null,
            ipAssignmentPools: List<IPRange>? = null,
            mtu: Long? = null,
            multicastLimit: Int? = null,
            name: String,
            private: Boolean,
            routes: List<Route>? = null,
            rules: List<Rule>? = null,
            v4AssignMode: IPV4AssignMode? = null,
            v6AssignMode: IPV6AssignMode? = null,
        ): NetworkConfig {
            return NetworkConfig(
                enableBroadcast = enableBroadcast,
                ipAssignmentPools = ipAssignmentPools,
                mtu = mtu,
                multicastLimit = multicastLimit,
                name = name,
                private = private,
                routes = routes,
                rules = rules,
                v4AssignMode = v4AssignMode,
                v6AssignMode = v6AssignMode,
            )
        }
    }
}

@Serializable
data class IPRange(
    val ipRangeStart: String,
    val ipRangeEnd: String,
)

@Serializable
data class Route(
    val target: String,
    val via: String? = null
)

@Serializable
data class Rule(
    val eitherType: Long? = null,
    val not: Boolean? = null,
    val or: Boolean? = null,
    val type: String? = null,
)

@Serializable
data class IPV4AssignMode(
    val zt: Boolean
)

@Serializable
data class IPV6AssignMode(
    @SerialName("6plane") val _6plane: Boolean?,
    val rfc4193: Boolean?,
    val zt: Boolean?,
)

@Serializable
data class Member(
    val id: String? = null,
    val clock: Long? = null,
    val networkId: String? = null,
    val nodeId: String? = null,
    // deprecated val controllerId: String? = null,
    val hidden: Boolean? = null,
    val name: String? = null,
    val description: String? = null,
    val config: MemberConfig? = null,
    val lastOnline: Long? = null,
    val physicalAddress: String? = null,
    val clientVersion: String? = null,
    val protocolVersion: String? = null,
    val supportsRulesEngine: Boolean? = null,
) {
    companion object Factory {
        fun forCreate(
            name: String,
            config: MemberConfig,
            hidden: Boolean? = null,
            description: String? = null,
        ) : Member {
            return Member(
                name = name,
                config = config,
                hidden = hidden,
                description = description
            )
        }
    }
}

@Serializable
data class MemberConfig(
    val activeBridge: Boolean? = null,
    val authorized: Boolean? = null,
    val capabilities: List<Int>? = null,
    val creationTime: Long? = null,
    val id: String? = null,
    val identity: String? = null,
    val ipAssignments: List<String>? = null,
    val lastAuthorizedTime: Long? = null,
    val lastDeauthorizedTime: Long? = null,
    val noAutoAssignIps: Boolean? = null,
    val revision: Int? = null,
    val tags: List<List<String>>? = null,
    val vMajor: Int? = null,
    val vMinor: Int? = null,
    val vRev: Int? = null,
    val vProto: Int? = null,
){
    companion object Factory {
        fun forCreate(
            activeBridge: Boolean? = null,
            authorized: Boolean? = null,
            capabilities: List<Int>? = null,
            ipAssignments: List<String>? = null,
            noAutoAssignIps: Boolean? = null,
        ): MemberConfig {
            return MemberConfig(
                activeBridge = activeBridge,
                authorized = authorized,
                capabilities = capabilities,
                ipAssignments = ipAssignments,
                noAutoAssignIps = noAutoAssignIps
            )
        }
    }
}