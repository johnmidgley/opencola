package io.opencola.core.security

import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.X509v1CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.cert.X509Certificate

// This creates a V1 certificate. Would there ever be a case that a CA is needed? Would someone want to grant the
// ability to a trusted party to create certificates on their behalf?
// A history of certificates will be needed in order to validate historical transactions.
// Is it sufficient to replace a certificate (i.e. each user has only 1 active cert) vs. managing CRLs?
fun createCertificate(name: String, keyPair: KeyPair): X509Certificate {
    val x500NameBld = X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.O, "OpenCola").addRDN(BCStyle.CN, name)

    val x500Name = x500NameBld.build()

    // This is a v1 certificate - doesn't support extensions
    val certBuilder: X509v1CertificateBuilder = JcaX509v1CertificateBuilder(
        x500Name,
        BigInteger.valueOf(0), // calculateSerialNumber(),
        calculateDate(0),
        calculateDate(24 * 31),
        x500Name,
        keyPair.public
    )

    val signer: ContentSigner = JcaContentSignerBuilder(SIGNATURE_ALGO).setProvider("BC").build(keyPair.private)

    return JcaX509CertificateConverter().setProvider("BC")
        .getCertificate(certBuilder.build(signer))
}