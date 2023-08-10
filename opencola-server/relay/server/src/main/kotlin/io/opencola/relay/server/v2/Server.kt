package io.opencola.relay.server.v2

import io.opencola.model.Id
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.message.*
import io.opencola.relay.common.message.store.MemoryMessageStore
import io.opencola.relay.common.message.store.MessageStore
import io.opencola.relay.server.AbstractRelayServer
import io.opencola.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException

abstract class Server(numChallengeBytes: Int = 32, numSymmetricKeyBytes: Int = 32, messageStore: MessageStore = MemoryMessageStore()) :
    AbstractRelayServer(numChallengeBytes, numSymmetricKeyBytes, messageStore) {
    override suspend fun authenticate(socketSession: SocketSession): AuthenticationResult? {
        try {
            logger.debug { "Sending server identity" }
            // TODO: Figure out how to publish this key so client can ensure server is trusted
            socketSession.writeSizedByteArray(IdentityMessage(serverKeyPair.public).encodeProto())

            logger.debug { "Reading server challenge" }
            val serverChallenge = ChallengeMessage.decodeProto(socketSession.readSizedByteArray())

            logger.debug { "Writing challenge response" }
            val signature = sign(serverKeyPair.private, serverChallenge.challenge, serverChallenge.algorithm)
            socketSession.writeSizedByteArray(ChallengeResponse(signature).encodeProto())

            logger.debug { "Reading client identity" }
            val encryptedClientIdentity = EncryptedBytes.decodeProto(socketSession.readSizedByteArray())
            val clientIdentity = IdentityMessage.decodeProto(decrypt(serverKeyPair.private, encryptedClientIdentity))
            val clientPublicKey = clientIdentity.publicKey
            val clientId = Id.ofPublicKey(clientPublicKey)
            logger.info { "Authenticating $clientId" }
            if(!isAuthorized(clientPublicKey))
                throw RuntimeException("$clientId is not authorized")

            logger.debug { "Writing client challenge" }
            val clientChallenge = ChallengeMessage(DEFAULT_SIGNATURE_ALGO, random.nextBytes(numChallengeBytes))
            socketSession.writeSizedByteArray(clientChallenge.encodeProto())

            logger.debug { "Reading challenge response" }
            val encryptedChallengeResponse = EncryptedBytes.decodeProto(socketSession.readSizedByteArray())
            val clientChallengeResponse = ChallengeResponse.decodeProto(decrypt(serverKeyPair.private, encryptedChallengeResponse))

            val authenticationResult = if (
                clientChallengeResponse.signature.algorithm == DEFAULT_SIGNATURE_ALGO &&
                isValidSignature(clientPublicKey, clientChallenge.challenge, clientChallengeResponse.signature)
            ) {
                val sessionKey = random.nextBytes(numSymmetricKeyBytes)
                val encryptedSessionKey = encrypt(clientPublicKey, sessionKey)
                AuthenticationResult(AuthenticationStatus.AUTHENTICATED, encryptedSessionKey)
            } else
                AuthenticationResult(AuthenticationStatus.FAILED_CHALLENGE, null)

            socketSession.writeSizedByteArray(authenticationResult.encodeProto())

            if (authenticationResult.status != AuthenticationStatus.AUTHENTICATED)
                throw RuntimeException("$clientId failed to authenticate: $authenticationResult.status")

            logger.debug { "Client authenticated" }
            return AuthenticationResult(clientPublicKey)
        } catch (e: CancellationException) {
            // Let job cancellation fall through
        } catch (e: ClosedReceiveChannelException) {
            // Don't bother logging on closed connections
        } catch (e: Exception) {
            logger.warn { "$e" }
            socketSession.close()
        }

        return null
    }

    override fun decodePayload(payload: ByteArray): Envelope {
        return Envelope.decodeProto(payload)
    }
}