package io.opencola.relay.server.v1

import io.opencola.model.Id
import io.opencola.serialization.codecs.IntByteArrayCodec
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v1.PayloadEnvelope
import io.opencola.relay.server.AbstractRelayServer
import io.opencola.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.security.PublicKey

abstract class Server(numChallengeBytes: Int = 32) : AbstractRelayServer(numChallengeBytes){
    override suspend fun authenticate(socketSession: SocketSession): PublicKey? {
        try {
            logger.debug { "Authenticating" }
            val encodedPublicKey = socketSession.readSizedByteArray()
            val publicKey = publicKeyFromBytes(encodedPublicKey)

            logger.debug {"Received public key: ${Id.ofPublicKey(publicKey)}"}

            // Send challenge
            logger.debug { "Sending challenge" }
            val challenge = ByteArray(numChallengeBytes).also { random.nextBytes(it) }
            socketSession.writeSizedByteArray(challenge)

            // Read signed challenge
            val challengeSignature = socketSession.readSizedByteArray()
            logger.debug { "Received challenge signature" }

            val status = if (isValidSignature(publicKey, challenge, challengeSignature)) 0 else -1
            socketSession.writeSizedByteArray(IntByteArrayCodec.encode(status))
            if (status != 0)
                throw RuntimeException("Challenge signature is not valid")

            logger.debug { "Client authenticated" }
            return publicKey
        } catch (e: CancellationException) {
            // Let job cancellation fall through
        } catch (e: ClosedReceiveChannelException) {
            // Don't bother logging on closed connections
        }  catch (e: Exception) {
            logger.warn { "Client failed to authenticate: $e" }
            socketSession.close()
        }

        return null
    }

    override fun encodePayload(to: PublicKey, envelope: Envelope): ByteArray {
        require(to == envelope.recipients.single().publicKey) { "Recipient mismatch" }
        require(envelope.message.signature == Signature.none) { "Unexpected signature" }

        // In V1, message is already encoded and ready to go.
        return envelope.message.bytes
    }

    override fun decodePayload(from: PublicKey, payload: ByteArray): Envelope {
        val payloadEnvelope = PayloadEnvelope.decode(payload)
        return Envelope(
            Recipient(payloadEnvelope.to, encrypt(payloadEnvelope.to, "".toByteArray())),
            null,
            // Just pass message through (It's encrypted already for the recipient) - encodePayload() knows what to do.
            SignedBytes(Signature.none, payloadEnvelope.message)
        )
    }
}