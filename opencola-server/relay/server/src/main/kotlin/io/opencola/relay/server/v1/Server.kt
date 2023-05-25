package io.opencola.relay.server.v1

import io.opencola.model.Id
import io.opencola.security.isValidSignature
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.codecs.IntByteArrayCodec
import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.server.AbstractRelayServer
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

    override fun decodePayload(payload: ByteArray): Envelope {
        return Envelope.decode(payload)
    }


}