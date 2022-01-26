package opencola.core.security

import opencola.core.extensions.toByteArray
import opencola.core.model.Id
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v1CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
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

fun calculateDate(hoursInFuture: Int) : Date {
    val secs = System.currentTimeMillis() / 1000;
    return Date((secs + (hoursInFuture * 60 * 60)) * 1000);
}

fun createAuthority(){
    // Need to store full credential (i.e. including private key - private key is not in cert)
    val id = Id.ofData(UUID.randomUUID().toByteArray())
    val keyPair = generateKeyPair()
    val cert = createCertificate(id, keyPair)


}


// This creates a V1 certificate. Would there ever be a case that a CA is needed? Would someone want to grant the
// ability to a trusted party to create certificates on their behalf?
// A history of certificates will be needed in order to validate historical transactions.
// Is it sufficient to replace a certificate (i.e. each user has only 1 active cert) vs. managing CRLs?
fun createCertificate(id: Id, keyPair: KeyPair): X509Certificate {
    val x500NameBld = X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.O, "openCOLA").addRDN(BCStyle.CN, "opencola:$id")

    val name = x500NameBld.build()

    // This is a v1 certificate - doesn't support extensions
    val certBuilder: X509v1CertificateBuilder = JcaX509v1CertificateBuilder(
        name,
        BigInteger.valueOf(0), // calculateSerialNumber(),
        calculateDate(0),
        calculateDate(24 * 31),
        name,
        keyPair.public
    )

    val signer: ContentSigner = JcaContentSignerBuilder(SIGNATURE_ALGO).setProvider("BC").build(keyPair.private)

    return JcaX509CertificateConverter().setProvider("BC")
        .getCertificate(certBuilder.build(signer))
}