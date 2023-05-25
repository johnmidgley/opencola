package io.opencola.relay.client.v2

import io.opencola.relay.client.AbstractClient
import io.opencola.security.*
import io.opencola.relay.common.*
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.message.*
import io.opencola.relay.common.message.Message
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey

abstract class Client(
    uri: URI,
    keyPair: KeyPair,
    name: String? = null,
    requestTimeoutMilliseconds: Long = 60000, // TODO: Make configurable
    retryPolicy: (Int) -> Long = retryExponentialBackoff(),
) : AbstractClient(uri, keyPair, "$name", requestTimeoutMilliseconds, retryPolicy) {
    override suspend fun authenticate(socketSession: SocketSession) {
        // Send public key
        logger.debug { "Sending public key" }
        socketSession.writeSizedByteArray(ConnectMessage(keyPair.public).encodeProto())

        // Read challenge
        logger.debug { "Reading challenge" }
        val challengeMessage = ChallengeMessage.decodeProto(socketSession.readSizedByteArray())

        // Sign challenge and send back
        logger.debug { "Signing challenge" }
        val signature = sign(keyPair.private, challengeMessage.challenge, challengeMessage.algorithm)
        socketSession.writeSizedByteArray(ChallengeResponse(signature).encodeProto())

        val authenticationResult = AuthenticationResult.decodeProto(socketSession.readSizedByteArray())
        if (authenticationResult.status != AuthenticationStatus.AUTHENTICATED) {
            throw RuntimeException("Unable to authenticate connection: $authenticationResult.status")
        }

        logger.debug { "Authenticated" }
    }

    override fun getEncodeEnvelope(to: PublicKey, message: Message): ByteArray {
        return Envelope(to, null, encrypt(to, message.encodeProto()).encodeProto()).encodeProto()
    }

    override fun decodePayload(payload: ByteArray): Message {
        return Message.decodeProto(decrypt(keyPair.private, EncryptedBytes.decodeProto(payload))).validate()
    }
}