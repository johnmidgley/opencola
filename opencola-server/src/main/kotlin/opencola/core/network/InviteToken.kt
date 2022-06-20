package opencola.core.network

import opencola.core.content.Base58
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.security.Signator
import opencola.core.security.isValidSignature
import opencola.core.serialization.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.PublicKey
import java.time.Instant

val emptyByteArray = "".toByteArray()

data class InviteToken(
    val authorityId: Id,
    val name: String,
    val publicKey: PublicKey,
    val address: URI,
    val imageUri: URI,
    val tokenId: Id = Id.new(),
    val epochSecond: Long = Instant.now().epochSecond,
    val body: ByteArray = emptyByteArray) {

    fun toAuthority(rootAuthorityId: Id) : Authority {
        val image = imageUri.let { if (it.toString().isBlank()) null else imageUri }
        return Authority(rootAuthorityId, publicKey, address, name, imageUri = image)
    }

    fun encode(signator: Signator) : String {
        val token =  ByteArrayOutputStream().use{
            Id.encode(it, authorityId)
            it.writeString(name)
            it.writeByteArray(PublicKeyByteArrayCodec.encode(publicKey))
            it.writeUri(address)
            it.writeUri(imageUri)
            Id.encode(it, tokenId)
            it.writeLong(epochSecond)
            it.writeByteArray(body)
            it.toByteArray()
        }
        val signature = signator.signBytes(authorityId, token)
        val signedToken = ByteArrayOutputStream().use {
            it.writeByteArray(token)
            it.writeByteArray(signature)
            it.toByteArray()
        }

        return Base58.encode(signedToken)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InviteToken

        if (authorityId != other.authorityId) return false
        if (name != other.name) return false
        if (publicKey != other.publicKey) return false
        if (address != other.address) return false
        if (imageUri != other.imageUri) return false
        if (tokenId != other.tokenId) return false
        if (epochSecond != other.epochSecond) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = authorityId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + imageUri.hashCode()
        result = 31 * result + tokenId.hashCode()
        result = 31 * result + epochSecond.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }

    companion object Factory {
        private fun toInviteToken(body: ByteArray): InviteToken {
            return ByteArrayInputStream(body).use {
                InviteToken(
                    Id.decode(it),
                    it.readString(),
                    PublicKeyByteArrayCodec.decode(it.readByteArray()),
                    it.readUri(),
                    it.readUri(),
                    Id.decode(it),
                    it.readLong(),
                    it.readByteArray(),
                )
            }
        }

        fun fromAuthority(authority: Authority): InviteToken {
            return InviteToken(
                authority.authorityId,
                authority.name ?: "",
                authority.publicKey!!,
                authority.uri ?: throw IllegalArgumentException("Can't create InviteToken for authority with no address (uri)"),
                authority.imageUri ?: URI("")
            )
        }

        fun decode(token: String) : InviteToken {
            try {
                val signedToken = Base58.decode(token)

                return ByteArrayInputStream(signedToken).use {
                    val token = it.readByteArray()
                    val signature = it.readByteArray()
                    val inviteToken = toInviteToken(token)

                    if (!isValidSignature(inviteToken.publicKey, token, signature))
                        throw IllegalArgumentException("Invalid signature found in InviteToken.")

                    inviteToken
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid Invite Token. Request a new one.", e)
            }
        }
    }
}