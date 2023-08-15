package io.opencola.relay.server.v2

import io.opencola.model.Id
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.message.v1.Envelope
import io.opencola.relay.common.message.v2.*
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.relay.common.message.v2.store.MessageStore
import io.opencola.relay.server.AbstractRelayServer
import io.opencola.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.security.PublicKey

abstract class Server(numChallengeBytes: Int = 32, numSymmetricKeyBytes: Int = 32, messageStore: MessageStore = MemoryMessageStore()) :
    AbstractRelayServer(numChallengeBytes, numSymmetricKeyBytes, messageStore) {
    override suspend fun authenticate(socketSession: SocketSession): PublicKey?{
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

            val status = if (
                clientChallengeResponse.signature.algorithm == DEFAULT_SIGNATURE_ALGO &&
                isValidSignature(clientPublicKey, clientChallenge.challenge, clientChallengeResponse.signature)
            ) {
                AuthenticationStatus.AUTHENTICATED
            } else
                AuthenticationStatus.FAILED_CHALLENGE

            socketSession.writeSizedByteArray(AuthenticationResult(status).encodeProto())

            if (status != AuthenticationStatus.AUTHENTICATED)
                throw RuntimeException("$clientId failed to authenticate: $status")

            logger.debug { "Client authenticated" }
            return clientPublicKey
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
        val envelopeV2 = EnvelopeV2.decodeProto(payload)
        val to = PublicKeyByteArrayCodec.decode(decrypt(serverKeyPair.private, envelopeV2.to))
        // TODO: Make EnvelopeV2 a subclass of AbstractEnvelope and return it vs. creating a v1 Envelope - required for multi recipients
        return Envelope(to, envelopeV2.key, envelopeV2.message)
    }
}