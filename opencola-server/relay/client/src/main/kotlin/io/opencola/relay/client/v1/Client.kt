package io.opencola.relay.client.v1

import io.opencola.relay.client.AbstractClient
import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.Message
import io.opencola.relay.common.connection.SocketSession
import io.opencola.security.*
import io.opencola.serialization.codecs.IntByteArrayCodec
import io.opencola.relay.common.retryExponentialBackoff
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey

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
        socketSession.writeSizedByteArray(keyPair.public.encoded)

        // Read challenge
        logger.debug { "Reading challenge" }
        val challengeBytes = socketSession.readSizedByteArray()

        // Sign challenge and send back
        logger.debug { "Signing challenge" }
        socketSession.writeSizedByteArray(sign(keyPair.private, challengeBytes).bytes)

        val authenticationResponse = IntByteArrayCodec.decode(socketSession.readSizedByteArray())
        if (authenticationResponse != 0) {
            throw RuntimeException("Unable to authenticate connection: $authenticationResponse")
        }

        logger.debug { "Authenticated" }
    }

    override fun getEncodeEnvelope(to: PublicKey, message: Message): ByteArray {
        return Envelope(to, null, encrypt(to, message.encode()).bytes).encode()
    }

    override fun decodePayload(payload: ByteArray): Message {
        return Message.decode(decrypt(keyPair.private, payload)).validate()
    }
}