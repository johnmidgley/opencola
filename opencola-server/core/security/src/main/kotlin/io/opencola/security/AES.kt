/*
 * Copyright 2024 OpenCola
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

package io.opencola.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

private val secureRandom = SecureRandom()

fun generateAesKey(keySize: Int = 256): SecretKey {
    return KeyGenerator
        .getInstance("AES")
        .also { it.init(keySize) }
        .generateKey()
}

fun generateIv(size: Int = 16): IvParameterSpec {
    return IvParameterSpec(ByteArray(size).also { secureRandom.nextBytes(it) })
}

// TODO: Unify AES / ECC encrypt routines?
fun encrypt(
    secretKey: SecretKey,
    bytes: ByteArray,
    ivParameterSpec: IvParameterSpec = generateIv(),
    transformation: EncryptionTransformation = EncryptionTransformation.AES_CBC_PKCS5PADDING
): EncryptedBytes {
    val cipherBytes = Cipher
        .getInstance(transformation.transformationName)
        .also { it.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec) }
        .doFinal(bytes)

    return EncryptedBytes(
        transformation,
        EncryptionParameters(EncryptionParameters.Type.IV, ivParameterSpec.iv),
        cipherBytes
    )
}

fun decrypt(
    secretKey: SecretKey,
    bytes: ByteArray,
    ivParameterSpec: IvParameterSpec,
    transformation: EncryptionTransformation = EncryptionTransformation.AES_CBC_PKCS5PADDING
): ByteArray? {
    return Cipher
        .getInstance(transformation.transformationName)
        .also { it.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec) }
        .doFinal(bytes)
}

fun decrypt(secretKey: SecretKey, encryptedBytes: EncryptedBytes): ByteArray {
    require(encryptedBytes.parameters.type == EncryptionParameters.Type.IV)

    return Cipher
        .getInstance(encryptedBytes.transformation.transformationName)
        .also { it.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(encryptedBytes.parameters.value)) }
        .doFinal(encryptedBytes.bytes)
}