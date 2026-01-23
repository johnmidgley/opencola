/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.security.certificate

import io.opencola.security.DEFAULT_SIGNATURE_ALGO
import io.opencola.security.calculateDate
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

    val signer: ContentSigner = JcaContentSignerBuilder(DEFAULT_SIGNATURE_ALGO.algorithmName).setProvider("BC").build(keyPair.private)

    return JcaX509CertificateConverter().setProvider("BC")
        .getCertificate(certBuilder.build(signer))
}