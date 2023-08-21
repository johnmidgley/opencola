package io.opencola.relay.server.v2

import io.opencola.model.Id
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.v2.EnvelopeHeader
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
            val signedBytes = sign(serverKeyPair.private, serverChallenge.challenge, serverChallenge.algorithm)
            socketSession.writeSizedByteArray(ChallengeResponse(signedBytes.signature).encodeProto())

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

    override fun encodePayload(to: PublicKey, envelope: Envelope): ByteArray {
        return PayloadEnvelope.from(serverKeyPair.private, to, envelope).encodeProto()
    }

    override fun decodePayload(from: PublicKey, payload: ByteArray): Envelope {
        val envelopeV2 = PayloadEnvelope.decodeProto(payload)
        val envelopeHeader = EnvelopeHeader.decryptAndVerifySignature(serverKeyPair.private, from, envelopeV2.header)
        return Envelope(envelopeHeader.recipients.single(), envelopeHeader.messageStorageKey, envelopeV2.message)
    }
}