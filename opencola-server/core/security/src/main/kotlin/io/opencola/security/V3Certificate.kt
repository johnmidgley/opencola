package io.opencola.security

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension.*
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.StringWriter
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*

fun generateRSAKeyPair(keySize: Int = 2048): KeyPair {
    val kpGen = KeyPairGenerator.getInstance("RSA", "BC")
    kpGen.initialize(keySize, SecureRandom())
    return kpGen.generateKeyPair()
}

fun getSanNames(sans: Iterable<String>): Array<GeneralName> {
    return sans.map {
        if (it.startsWith("ip:")) {
            GeneralName(GeneralName.iPAddress, it.substring(3))
        } else if(it.startsWith("dns:")) {
            GeneralName(GeneralName.dNSName, it.substring(4))
        } else {
            throw IllegalArgumentException("Unhandled san: $it, should start with ip: or dns:")
        }
    }.toTypedArray()
}

fun generateSelfSignedV3Certificate(
    dirName: String,
    sans: List<String> = emptyList(),
    keyPair: KeyPair = generateRSAKeyPair(),
    lifetimeDays: Int = 365 * 10
): X509Certificate {
    val extUtils = JcaX509ExtensionUtils()
    val v3CertGen = JcaX509v3CertificateBuilder(
        X500Name(dirName),
        BigInteger(128, SecureRandom()),
        Date(System.currentTimeMillis()),
        Date(System.currentTimeMillis() + (lifetimeDays * 86400000L)),
        X500Name(dirName),
        keyPair.public
    )

    v3CertGen.addExtension(
        basicConstraints, true,
        BasicConstraints(true)
    )

    if(sans.isNotEmpty()) {
        v3CertGen.addExtension(
            subjectAlternativeName, false,
            GeneralNames(getSanNames(sans))
        )
    }

    v3CertGen.addExtension(
        subjectKeyIdentifier, false,
        extUtils.createSubjectKeyIdentifier(keyPair.public) as ASN1Encodable
    )

    return JcaX509CertificateConverter()
        .setProvider("BC")
        .getCertificate(
            v3CertGen
                .build(JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC")
                .build(keyPair.private))
        )
}

fun convertCertificateToPEM(signedCertificate: X509Certificate): String {
    val signedCertificatePEMDataStringWriter = StringWriter()
    val pemWriter = JcaPEMWriter(signedCertificatePEMDataStringWriter)
    pemWriter.writeObject(signedCertificate)
    pemWriter.close()
    return signedCertificatePEMDataStringWriter.toString()
}