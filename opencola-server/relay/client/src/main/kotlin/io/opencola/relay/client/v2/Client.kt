package io.opencola.relay.client.v2

import io.opencola.relay.client.AbstractClient
import io.opencola.security.*
import io.opencola.relay.common.*
import java.net.URI
import java.security.KeyPair

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
        socketSession.writeSizedByteArray(ConnectMessage(keyPair.public).encodeProto())

        // Read challenge
        logger.debug { "Reading challenge" }
        val challengeMessage = ChallengeMessage.decodeProto(socketSession.readSizedByteArray())

        // Sign challenge and send back
        logger.debug { "Signing challenge" }
        val signature = sign(keyPair.private, challengeMessage.challenge, challengeMessage.algorithm)
        socketSession.writeSizedByteArray( ChallengeResponse(signature).encodeProto())

        val authenticationResult = AuthenticationResult.decodeProto(socketSession.readSizedByteArray())
        if (authenticationResult.status != AuthenticationStatus.AUTHENTICATED) {
            throw RuntimeException("Unable to authenticate connection: $authenticationResult.status")
        }

        logger.debug { "Authenticated" }
    }

    override fun encodeEnvelope(envelope: Envelope): ByteArray {
        return envelope.encodeProto()
    }

    override fun decodeMessage(bytes: ByteArray): Message {
        return Message.decodeProto(bytes)
    }
}