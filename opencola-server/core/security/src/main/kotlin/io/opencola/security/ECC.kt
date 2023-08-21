package io.opencola.security

import io.opencola.util.Base58
import io.opencola.util.hexStringToByteArray
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher

// https://metamug.com/article/security/sign-verify-digital-signature-ecdsa-java.html

const val SPEC = "secp256r1" // "secp256k1"
const val KEY_ALGO = "EC"
val DEFAULT_SIGNATURE_ALGO = SignatureAlgorithm.SHA3_256_WITH_ECDSA
val DEFAULT_ENCRYPTION_TRANSFORMATION = EncryptionTransformation.ECIES_WITH_AES_CBC

fun generateKeyPair(): KeyPair {
    val ecSpec = ECGenParameterSpec(SPEC)
    val g = KeyPairGenerator.getInstance(KEY_ALGO)
    g.initialize(ecSpec, SecureRandom())
    return g.generateKeyPair()
}

fun sign(privateKey: PrivateKey, bytes: ByteArray, algorithm: SignatureAlgorithm = DEFAULT_SIGNATURE_ALGO): SignedBytes {
    val ecdsaSign = java.security.Signature.getInstance(algorithm.algorithmName).also {
        it.initSign(privateKey)
        it.update(bytes)
    }
    return SignedBytes(Signature(algorithm, ecdsaSign.sign()), bytes)
}

fun encrypt(
    publicKey: PublicKey,
    bytes: ByteArray,
    transformation: EncryptionTransformation = DEFAULT_ENCRYPTION_TRANSFORMATION
): EncryptedBytes {
    val cipher = Cipher.getInstance(transformation.transformationName).also { it.init(Cipher.ENCRYPT_MODE, publicKey) }
    val encryptionParameters = EncryptionParameters(EncryptionParameters.Type.IES, cipher.parameters.encoded)
    return EncryptedBytes(transformation, encryptionParameters, cipher.doFinal(bytes))
}

fun decrypt(
    privateKey: PrivateKey,
    bytes: ByteArray,
    parameters: EncryptionParameters,
    transformation: EncryptionTransformation = DEFAULT_ENCRYPTION_TRANSFORMATION
): ByteArray {
    require(parameters.type == EncryptionParameters.Type.IES)
    val params = AlgorithmParameters.getInstance(parameters.type.typeName).also { it.init(parameters.value) }
    return Cipher.getInstance(transformation.transformationName)
        .also { it.init(Cipher.DECRYPT_MODE, privateKey, params) }
        .doFinal(bytes)
}

fun decrypt(privateKey: PrivateKey, encryption: EncryptedBytes): ByteArray {
    return decrypt(privateKey, encryption.bytes, encryption.parameters, encryption.transformation)
}

fun isValidSignature(
    publicKey: PublicKey,
    data: ByteArray,
    signature: ByteArray,
    algorithm: SignatureAlgorithm = DEFAULT_SIGNATURE_ALGO
): Boolean {
    val ecdsaVerify = java.security.Signature.getInstance(algorithm.algorithmName)
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
fun publicKeyFromBytes(bytes: ByteArray): PublicKey {
    return getKeyFactory().generatePublic(X509EncodedKeySpec(bytes))
}

fun PublicKey.encode(): String {
    return Base58.encode(this.encoded)
}

fun decodePublicKey(value: String): PublicKey {
    return publicKeyFromBytes(
        when (value.length) {
            182 -> value.hexStringToByteArray()
            else -> Base58.decode(value)
        }
    )
}

fun privateKeyFromBytes(bytes: ByteArray): PrivateKey {
    return getKeyFactory().generatePrivate(PKCS8EncodedKeySpec(bytes))
}

fun decodePrivateKey(value: String): PrivateKey {
    return privateKeyFromBytes(value.hexStringToByteArray())
}

fun calculateDate(hoursInFuture: Int): Date {
    val secs = System.currentTimeMillis() / 1000
    return Date((secs + (hoursInFuture * 60 * 60)) * 1000)
}
