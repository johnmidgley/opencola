package io.opencola.relay.client.v2

import io.opencola.relay.client.AbstractClient
import io.opencola.security.*
import io.opencola.relay.common.*
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.message.Message
import io.opencola.relay.common.message.v2.*
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
        logger.info { "Authenticating" }

        logger.debug { "Reading server identity" }
        val serverPublicKey = IdentityMessage.decodeProto(socketSession.readSizedByteArray()).publicKey
        logger.debug { "Server public key: $serverPublicKey" }
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
        val signature = sign(keyPair.private, clientChallenge.challenge, clientChallenge.algorithm)
        // Encrypt the signature with the server's public key so that MITM can't identify clients based on known public keys
        val encryptedChallengeResponse = encrypt(serverPublicKey, ChallengeResponse(signature).encodeProto())
        socketSession.writeSizedByteArray(encryptedChallengeResponse.encodeProto())

        logger.debug { "Reading authentication result" }
        val authenticationResult = AuthenticationResult.decodeProto(socketSession.readSizedByteArray())
        if (authenticationResult.status != AuthenticationStatus.AUTHENTICATED) {
            throw RuntimeException("Unable to authenticate connection: $authenticationResult.status")
        }

        this.serverPublicKey = serverPublicKey
        logger.debug { "Authenticated" }
    }

    override fun getEncodeEnvelope(to: PublicKey, key: MessageKey, message: Message): ByteArray {
        require(serverPublicKey != null) { "Not authenticated, cannot encode envelope without serverPublicKey" }
        return EnvelopeV2(
            encrypt(serverPublicKey!!, PublicKeyByteArrayCodec.encode(to)),
            key,
            encrypt(to, message.encodeProto()).encodeProto()
        ).encodeProto()
    }

    override fun decodePayload(payload: ByteArray): Message {
        return Message.decodeProto(decrypt(keyPair.private, EncryptedBytes.decodeProto(payload))).validate()
    }
}