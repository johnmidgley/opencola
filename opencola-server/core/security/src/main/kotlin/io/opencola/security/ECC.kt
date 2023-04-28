package io.opencola.security

import io.opencola.util.Base58
import io.opencola.util.hexStringToByteArray
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher

// https://metamug.com/article/security/sign-verify-digital-signature-ecdsa-java.html

const val SPEC = "secp256r1" // "secp256k1"
const val SIGNATURE_ALGO = "SHA3-256withECDSA" // "SHA256withECDSA"
const val KEY_ALGO = "EC"
const val ENCRYPTION_TRANSFORMATION = "ECIESwithAES-CBC"

fun generateKeyPair() : KeyPair {
    val ecSpec = ECGenParameterSpec(SPEC)
    val g = KeyPairGenerator.getInstance("EC")
    g.initialize(ecSpec, SecureRandom())
    return g.generateKeyPair()
}

fun sign(privateKey: PrivateKey, data: ByteArray, algorithm: String = SIGNATURE_ALGO): Signature {
    val ecdsaSign = java.security.Signature.getInstance(algorithm).also {
        it.initSign(privateKey)
        it.update(data)
    }
    return Signature(algorithm, ecdsaSign.sign())
}

fun encrypt(publicKey: PublicKey, bytes: ByteArray) : ByteArray {
    return ByteArrayOutputStream().use{
        val cipher = Cipher.getInstance(ENCRYPTION_TRANSFORMATION).also { it.init(Cipher.ENCRYPT_MODE, publicKey) }
        it.writeByteArray(cipher.parameters.encoded)
        it.writeByteArray(cipher.doFinal(bytes))
        it.toByteArray()
    }
}

fun decrypt(privateKey: PrivateKey, bytes: ByteArray) : ByteArray {
    ByteArrayInputStream(bytes).use{ stream ->
        val encodedParameters = stream.readByteArray()
        val cipherBytes = stream.readByteArray()
        val params = AlgorithmParameters.getInstance("IES").also { it.init(encodedParameters) }

        return Cipher.getInstance(ENCRYPTION_TRANSFORMATION)
            .also { it.init(Cipher.DECRYPT_MODE, privateKey, params) }
            .doFinal(cipherBytes)
    }
}

fun isValidSignature(publicKey: PublicKey, data: ByteArray, signature: ByteArray, algorithm: String = SIGNATURE_ALGO): Boolean {
    val ecdsaVerify = java.security.Signature.getInstance(algorithm)
    ecdsaVerify.initVerify(publicKey)
    ecdsaVerify.update(data)
    return ecdsaVerify.verify(signature)
}

fun isValidSignature(publicKey: PublicKey, data: ByteArray, signature: Signature): Boolean {
    return isValidSignature(publicKey, data, signature.bytes, signature.algorithm)
}

fun getKeyFactory(): KeyFactory {
    return KeyFactory.getInstance(KEY_ALGO)
        ?: throw MissingResourceException("Missing key factory provider", "KeyFactory", KEY_ALGO)
}

// https://docs.oracle.com/javase/tutorial/security/apisign/vstep2.html
fun publicKeyFromBytes(bytes: ByteArray) : PublicKey {
    return getKeyFactory().generatePublic(X509EncodedKeySpec(bytes))
}

fun PublicKey.encode() : String {
    return Base58.encode(this.encoded)
}

fun decodePublicKey(value: String) : PublicKey {
    return publicKeyFromBytes(
        when(value.length){
            182 -> value.hexStringToByteArray()
            else -> Base58.decode(value)
        }
    )
}

fun privateKeyFromBytes(bytes: ByteArray) : PrivateKey {
    return getKeyFactory().generatePrivate(PKCS8EncodedKeySpec(bytes))
}

fun decodePrivateKey(value: String) : PrivateKey {
    return privateKeyFromBytes(value.hexStringToByteArray())
}

fun calculateDate(hoursInFuture: Int) : Date {
    val secs = System.currentTimeMillis() / 1000
    return Date((secs + (hoursInFuture * 60 * 60)) * 1000)
}
