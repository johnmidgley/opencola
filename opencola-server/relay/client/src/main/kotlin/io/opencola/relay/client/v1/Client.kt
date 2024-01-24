/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.relay.client.v1

import io.opencola.relay.client.AbstractClient
import io.opencola.relay.common.message.v1.PayloadEnvelope
import io.opencola.relay.common.message.Message
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v1.MessageV1
import io.opencola.relay.common.message.v2.AuthenticationStatus
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
    connectTimeoutMilliseconds: Long = 3000, // TODO: Make configurable
    requestTimeoutMilliseconds: Long = 60000, // TODO: Make configurable
    retryPolicy: (Int) -> Long = retryExponentialBackoff(),
) : AbstractClient(uri, keyPair, name, connectTimeoutMilliseconds, requestTimeoutMilliseconds, retryPolicy) {

    // Should only be called once, right after connection to server
    override suspend fun authenticate(socketSession: SocketSession) : AuthenticationStatus {
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
        return AuthenticationStatus.AUTHENTICATED
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

    override fun encodePayload(to: List<PublicKey>, messageStorageKey: MessageStorageKey, message: Message): List<ByteArray> {
        return to.map { PayloadEnvelope(it, encryptV1(it, MessageV1(keyPair, message.body).encode())).encode() }
    }

    override fun decodePayload(payload: ByteArray): Envelope {
        // V1 relay doesn't support per client symmetric key encryption, so we need to generate a dummy key
        val dummyEncryptedMessageKey = encrypt(keyPair.public, generateAesKey().encoded)
        val recipient = Recipient(keyPair.public, dummyEncryptedMessageKey)

        // Repackage old custom V1 encryption format into standard EncryptedBytes so that Envelope can decrypt
        val encryptedBytes = ByteArrayInputStream(payload).use { stream ->
            val encodedParameters = stream.readByteArray()
            val cipherBytes = stream.readByteArray()
            EncryptedBytes(
                EncryptionTransformation.ECIES_WITH_AES_CBC,
                EncryptionParameters(EncryptionParameters.Type.IES, encodedParameters),
                cipherBytes
            )
        }

        val signedBytes = SignedBytes(Signature.none, encryptedBytes.encodeProto())

        return Envelope(recipient, null, signedBytes)
    }
}