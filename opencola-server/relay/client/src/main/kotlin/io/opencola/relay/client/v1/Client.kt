package io.opencola.relay.client.v1

import io.opencola.relay.client.AbstractClient
import io.opencola.relay.common.message.v1.PayloadEnvelope
import io.opencola.relay.common.message.Message
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v1.MessageV1
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.*
import io.opencola.serialization.codecs.IntByteArrayCodec
import io.opencola.relay.common.retryExponentialBackoff
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey
import javax.crypto.Cipher

abstract class Client(
    uri: URI,
    keyPair: KeyPair,
    name: String? = null,
    requestTimeoutMilliseconds: Long = 60000, // TODO: Make configurable
    retryPolicy: (Int) -> Long = retryExponentialBackoff(),
) : AbstractClient(uri, keyPair, name, requestTimeoutMilliseconds, retryPolicy) {

    // Should only be called once, right after connection to server
    override suspend fun authenticate(socketSession: SocketSession) {
        // Send public key
        logger.debug { "Sending public key" }
        socketSession.writeSizedByteArray(keyPair.public.encoded)

        // Read challenge
        logger.debug { "Reading challenge" }
        val challengeBytes = socketSession.readSizedByteArray()

        // Sign challenge and send back
        logger.debug { "Signing challenge" }
        socketSession.writeSizedByteArray(sign(keyPair.private, challengeBytes).signature.bytes)

        val authenticationResponse = IntByteArrayCodec.decode(socketSession.readSizedByteArray())
        if (authenticationResponse != 0) {
            throw RuntimeException("Unable to authenticate connection: $authenticationResponse")
        }

        logger.debug { "Authenticated" }
    }

    // OLD encryption code, only used in V1 client (doesn't use protobuf encoding)
    private fun encryptV1(
        publicKey: PublicKey,
        bytes: ByteArray,
        transformation: EncryptionTransformation = DEFAULT_ENCRYPTION_TRANSFORMATION
    ): ByteArray {
        return ByteArrayOutputStream().use {
            val cipher =
                Cipher.getInstance(transformation.transformationName).also { it.init(Cipher.ENCRYPT_MODE, publicKey) }
            it.writeByteArray(cipher.parameters.encoded)
            it.writeByteArray(cipher.doFinal(bytes))
            it.toByteArray()
        }
    }

    override fun encodePayload(to: PublicKey, messageStorageKey: MessageStorageKey, message: Message): ByteArray {
        return PayloadEnvelope(to, encryptV1(to, MessageV1(keyPair, message.body).encode())).encode()
    }

    override fun decodePayload(payload: ByteArray): Envelope {
        // V1 relay doesn't support per client symmetric key encryption, so we need to generate a dummy key
        val dummyEncryptedMessageKey = encrypt(keyPair.public, generateAesKey().encoded)
        val recipient = Recipient(keyPair.public, dummyEncryptedMessageKey)

        val encryptedBytes = ByteArrayInputStream(payload).use { stream ->
            val encodedParameters = stream.readByteArray()
            val cipherBytes = stream.readByteArray()
            EncryptedBytes(
                EncryptionTransformation.ECIES_WITH_AES_CBC,
                EncryptionParameters(EncryptionParameters.Type.IES, encodedParameters),
                cipherBytes
            )
        }

        return Envelope(recipient, null, encryptedBytes)
    }
}