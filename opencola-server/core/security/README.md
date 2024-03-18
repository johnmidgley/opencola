<img src="../../../img/pull-tab.svg" width="150" />

# security

The security library contains code for hashing, encrypting and signing data in OpenCola and uses the ```BouncyCastleProvider```.

* **Hashing**: All hashing in OpenCola is done using the ```SHA-256``` digest algorithm. These hashes are used mainly to produce unique ```Id```s.

* **Encrypting**: Things that is encrypted for specific recipients are done using [ECC](./src/main/kotlin/io/opencola/security/ECC.kt), using the ```secp256r1``` curve and ```ECIES_WITH_AES_CBC``` transformation. Things that are encrypted for multiple recipients are encrypted using 256 bit [AES](./bin/main/io/opencola/security/AES.kt). The ```AES``` key is then encrypted using ```ECC``` for each recipient.

* **Signing**: Data is [signed](./src/main/kotlin/io/opencola/security/hash/Sha256Hash.kt) using ```SHA3_256_WITH_ECDSA```




