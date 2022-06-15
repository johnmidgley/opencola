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
import java.util.*

data class InviteToken(
    val authorityId: Id,
    val name: String,
    val publicKey: PublicKey,
    val address: URI,
    val imageUri: URI,
    val tokenId: String = UUID.randomUUID().toString(),
    val epochSecond: Long = Instant.now().epochSecond) {

    fun toAuthority(rootAuthorityId: Id) : Authority {
        val image = imageUri.let { if (it.toString().isBlank()) null else imageUri }
        return Authority(rootAuthorityId, publicKey, address, name, imageUri = image)
    }

    fun encode(signator: Signator) : String {
        val body =  ByteArrayOutputStream().use{
            Id.encode(it, authorityId)
            it.writeString(name)
            it.writeByteArray(PublicKeyByteArrayCodec.encode(publicKey))
            it.writeUri(address)
            it.writeUri(imageUri)
            it.writeString(tokenId)
            it.writeLong(epochSecond)
            it.toByteArray()
        }
        val signature = signator.signBytes(authorityId, body)
        val message = ByteArrayOutputStream().use {
            it.writeByteArray(body)
            it.writeByteArray(signature)
            it.toByteArray()
        }

        return Base58.encode(message)
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
                    it.readString(),
                    it.readLong(),
                )
            }
        }

        fun fromAuthority(authority: Authority): InviteToken {
            return InviteToken(
                authority.authorityId,
                authority.name ?: "",
                authority.publicKey!!,
                authority.uri ?: throw IllegalArgumentException("Can't create InviteToken for authority with now address (uri)"),
                authority.imageUri ?: URI("")
            )
        }

        fun decode(token: String) : InviteToken {
            try {
                val message = Base58.decode(token)

                return ByteArrayInputStream(message).use {
                    val body = it.readByteArray()
                    val signature = it.readByteArray()
                    val inviteToken = toInviteToken(body)

                    if (!isValidSignature(inviteToken.publicKey, body, signature))
                        throw IllegalArgumentException("Invalid signature found in InviteToken.")

                    inviteToken
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid Invite Token. Request a new one.", e)
            }
        }
    }
}