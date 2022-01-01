package opencola.core.security

import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*


// https://metamug.com/article/security/sign-verify-digital-signature-ecdsa-java.html

const val SPEC = "secp256r1" // "secp256k1"
const val SIGNATURE_ALGO = "SHA3-256withECDSA" // "SHA256withECDSA"
const val KEY_ALGO = "EC"

fun generateKeyPair() : KeyPair {
    val ecSpec = ECGenParameterSpec(SPEC)
    val g = KeyPairGenerator.getInstance("EC")
    g.initialize(ecSpec, SecureRandom())
    return g.generateKeyPair()
}

fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray {
    val ecdsaSign = Signature.getInstance(SIGNATURE_ALGO)
    ecdsaSign.initSign(privateKey)
    ecdsaSign.update(data)
    return ecdsaSign.sign()
}

fun isValidSignature(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean {
    val ecdsaVerify = Signature.getInstance(SIGNATURE_ALGO)
    ecdsaVerify.initVerify(publicKey)
    ecdsaVerify.update(data)
    return ecdsaVerify.verify(signature)
}

fun getKeyFactory(): KeyFactory {
    return KeyFactory.getInstance(KEY_ALGO)
        ?: throw MissingResourceException("Missing key factory provider", "KeyFactory", KEY_ALGO)
}

// https://docs.oracle.com/javase/tutorial/security/apisign/vstep2.html
fun publicKeyFromBytes(bytes: ByteArray) : PublicKey {
    return getKeyFactory().generatePublic(X509EncodedKeySpec(bytes))
}

fun privateKeyFromBytes(bytes: ByteArray) : PrivateKey {
    return getKeyFactory().generatePrivate(PKCS8EncodedKeySpec(bytes))
}