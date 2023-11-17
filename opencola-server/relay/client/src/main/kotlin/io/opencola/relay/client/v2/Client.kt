package io.opencola.relay.client.v2

import io.opencola.model.Id
import io.opencola.relay.client.AbstractClient
import io.opencola.security.*
import io.opencola.relay.common.*
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.Message
import io.opencola.relay.common.message.v2.*
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey

// TODO: Factor out configuration
abstract class Client(
    uri: URI,
    keyPair: KeyPair,
    name: String? = null,
    connectTimeoutMilliseconds: Long = 3000,
    requestTimeoutMilliseconds: Long = 60000,
    retryPolicy: (Int) -> Long = retryExponentialBackoff(),
) : AbstractClient(uri, keyPair, "$name", connectTimeoutMilliseconds, requestTimeoutMilliseconds, retryPolicy) {
    override suspend fun authenticate(socketSession: SocketSession) : AuthenticationStatus {
        logger.info { "Authenticating" }

        logger.debug { "Reading server identity" }
        val serverPublicKey = IdentityMessage.decodeProto(socketSession.readSizedByteArray()).publicKey
        logger.debug { "Server public Id: ${Id.ofPublicKey(serverPublicKey)}" }
        if (!isAuthorized(serverPublicKey))
            throw RuntimeException("Server is not authorized: $serverPublicKey")

        logger.debug { "Writing server challenge" }
        val serverChallenge = ChallengeMessage(DEFAULT_SIGNATURE_ALGO, random.nextBytes(numChallengeBytes))
        socketSession.writeSizedByteArray(serverChallenge.encodeProto())

        logger.debug { "Reading challenge response" }
        val serverChallengeResponse = ChallengeResponse.decodeProto(socketSession.readSizedByteArray())
        if (!isValidSignature(serverPublicKey, serverChallenge.challenge, serverChallengeResponse.signature))
            throw RuntimeException("Server failed challenge")

        logger.debug { "Writing client identity" }
        val encryptedIdentityMessage = encrypt(serverPublicKey, IdentityMessage(keyPair.public).encodeProto())
        socketSession.writeSizedByteArray(encryptedIdentityMessage.encodeProto())

        logger.debug { "Reading client challenge" }
        val clientChallenge = ChallengeMessage.decodeProto(socketSession.readSizedByteArray())

        logger.debug { "Writing challenge response" }
        val signedBytes = sign(keyPair.private, clientChallenge.challenge, clientChallenge.algorithm)
        // Encrypt the signature with the server's public key so that MITM can't identify clients based on known public keys
        val encryptedChallengeResponse =
            encrypt(serverPublicKey, ChallengeResponse(signedBytes.signature).encodeProto())
        socketSession.writeSizedByteArray(encryptedChallengeResponse.encodeProto())

        logger.debug { "Reading authentication result" }
        val authenticationResult = AuthenticationResult.decodeProto(socketSession.readSizedByteArray())
        if (authenticationResult.status == AuthenticationStatus.AUTHENTICATED)
            this.serverPublicKey = serverPublicKey

        logger.debug { "AuthenticationStatus: ${authenticationResult.status}" }

        return authenticationResult.status
    }

    override fun encodePayload(
        to: List<PublicKey>,
        messageStorageKey: MessageStorageKey,
        message: Message
    ): List<ByteArray> {
        if(serverPublicKey == null) runBlocking { connect() }
        return listOf(PayloadEnvelope.encodePayload(keyPair.private, serverPublicKey!!, to, messageStorageKey, message))
    }

    override fun decodePayload(payload: ByteArray): Envelope {
        require(serverPublicKey != null) { "Not authenticated, cannot decode envelope without serverPublicKey" }
        val envelope = PayloadEnvelope.decodePayload(keyPair.private, serverPublicKey!!, payload)

        if (envelope.recipients.count() != 1)
            throw RuntimeException("Expected 1 recipient, got ${envelope.recipients.count()}")

        val recipient = envelope.recipients.single()

        if (recipient.publicKey != publicKey)
            throw RuntimeException("Received message for unknown public key")

        return envelope
    }
}