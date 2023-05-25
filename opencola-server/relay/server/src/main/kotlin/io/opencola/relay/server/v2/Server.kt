package io.opencola.relay.server.v2

import io.opencola.model.Id
import io.opencola.relay.common.*
import io.opencola.security.isValidSignature
import io.opencola.relay.server.AbstractRelayServer
import io.opencola.security.SIGNATURE_ALGO
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.security.PublicKey

abstract class Server(numChallengeBytes: Int = 32) : AbstractRelayServer(numChallengeBytes) {
    override suspend fun authenticate(socketSession: SocketSession): PublicKey? {
        try {
            logger.debug { "Authenticating" }
            val connectMessage = ConnectMessage.decodeProto(socketSession.readSizedByteArray())
            val publicKey = connectMessage.publicKey
            val id = Id.ofPublicKey(publicKey)

            logger.debug { "Received public key: $id" }

            // Send challenge
            logger.debug { "Sending challenge" }
            val challenge = ByteArray(numChallengeBytes).also { random.nextBytes(it) }
            val challengeMessage = ChallengeMessage(SIGNATURE_ALGO, challenge)
            socketSession.writeSizedByteArray(challengeMessage.encodeProto())

            // Read signed challenge
            val challengeResponse = ChallengeResponse.decodeProto(socketSession.readSizedByteArray())
            logger.debug { "Received challenge signature" }

            val status = if (
                challengeResponse.signature.algorithm == SIGNATURE_ALGO &&
                isValidSignature(publicKey, challenge, challengeResponse.signature)
            )
                AuthenticationStatus.AUTHENTICATED
            else
                AuthenticationStatus.FAILED_CHALLENGE

            socketSession.writeSizedByteArray(AuthenticationResult(status).encodeProto())

            if (status != AuthenticationStatus.AUTHENTICATED)
                throw RuntimeException("$id failed to authenticate: $status")

            logger.debug { "Client authenticated" }
            return publicKey
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