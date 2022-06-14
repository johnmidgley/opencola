package opencola.core.network.zerotier

import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.nullOrElse
import java.net.URI

data class ZeroTierAddress(val networkId: String?, val nodeId: String?, val root: URI = defaultRoot) {

    init {
        // Validate ids are hex values that fit in Longs
        networkId.nullOrElse { it.hexStringToByteArray().size <= 16 }
        nodeId.nullOrElse { it.hexStringToByteArray().size <= 16 }
    }

    fun toURI() : URI {
        return URI("zt:${root}#${networkId ?: ""}:${nodeId ?: ""}")
    }

    companion object Factory {
        private val defaultRoot = URI("https://my.zerotier.com/api/v1")

        fun fromURI(uri: URI) : ZeroTierAddress? {
            if(uri.scheme != "zt")
                return null

            val root = URI(uri.schemeSpecificPart)
            val fragments = uri.fragment.split(":")

            if(fragments.size != 2){
                throw IllegalArgumentException("Invalid ZeroTier fragment: ${uri.fragment}. Must be of form {networkId?:nodeId?}")
            }

            return ZeroTierAddress(fragments[0].ifBlank { null } , fragments[1].ifBlank { null }, root)
        }
    }
}