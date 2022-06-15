package opencola.core.network

import opencola.core.content.Base58
import opencola.core.model.Id
import opencola.core.security.Signator
import opencola.core.security.isValidSignature
import opencola.core.serialization.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.PublicKey

data class InviteToken(
    private val authorityId: Id,
    private val name: String,
    private val publicKey: PublicKey,
    private val address: URI,
    private val imageUri: URI) {

    fun encode(signator: Signator) : String {
        val body =  ByteArrayOutputStream().use{
            Id.encode(it, authorityId)
            it.writeString(name)
            it.writeByteArray(PublicKeyByteArrayCodec.encode(publicKey))
            it.writeUri(address)
            it.writeUri(imageUri)
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
                    it.readUri()
                )
            }
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