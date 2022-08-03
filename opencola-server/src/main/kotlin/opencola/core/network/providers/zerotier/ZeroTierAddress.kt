package opencola.core.network.providers.zerotier

import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.nullOrElse
import java.net.URI

// TODO: Ids should be ZeroTierIds
data class ZeroTierAddress(val networkId: String?, val nodeId: String?, val port: Int?, val root: URI = defaultRoot) {

    init {
        // Validate ids are hex values that fit in Longs
        networkId.nullOrElse { it.hexStringToByteArray().size <= 16 }
        nodeId.nullOrElse { it.hexStringToByteArray().size <= 16 }

        if(nodeId != null && port == null){
            throw IllegalArgumentException("Port must be provided for nodeId")
        }
    }

    fun toURI() : URI {
        return URI("zt:${root}#${networkId ?: ""}:${nodeId ?: ""}:${port ?: ""}")
    }

    companion object Factory {
        private val defaultRoot = URI("https://my.zerotier.com/api/v1")

        fun fromURI(uri: URI) : ZeroTierAddress? {
            if(uri.scheme != "zt")
                return null

            val root = URI(uri.schemeSpecificPart)
            val fragments = uri.fragment.split(":")

            if(fragments.size != 3){
                throw IllegalArgumentException("Invalid ZeroTier fragment: ${uri.fragment}. Must be of form {networkId?:nodeId?}")
            }

            val port = fragments[2].ifBlank { null }.nullOrElse { it.toInt() }
            return ZeroTierAddress(fragments[0].ifBlank { null }, fragments[1].ifBlank { null }, port, root)
        }
    }
}