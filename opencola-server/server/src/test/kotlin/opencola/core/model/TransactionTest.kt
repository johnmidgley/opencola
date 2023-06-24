package opencola.core.model

import io.opencola.model.*
import io.opencola.application.TestApplication
import io.opencola.model.value.StringValue
import io.opencola.util.toHexString
import io.opencola.security.SIGNATURE_ALGO
import io.opencola.security.Signator
import io.opencola.security.Signature
import io.opencola.security.sha256
import io.opencola.serialization.EncodingFormat
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.util.CompressedBytes
import io.opencola.util.CompressionFormat
import io.opencola.util.compress
import org.junit.Test
import org.kodein.di.instance
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class TransactionTest {
    private val app = TestApplication.instance
    private val persona = app.inject<AddressBook>().getEntries().filterIsInstance<PersonaAddressBookEntry>().first()
    private val signator by app.injector.instance<Signator>()

    @Test
    fun testTransactionStructure() {
        val stableStructureHash = "8d5f46320d50ee8032b8ef57093a2bf3cecab37ea6628813a8baa368636dd55f"
        val id = Id.ofData("".toByteArray())
        val fact = Fact(id, id, CoreAttribute.Type.spec, StringValue("").asAnyValue(), Operation.Add)
        val encodedTransaction = Transaction.fromFacts(id, listOf(fact), 0).encodeProto()
        val compressedTransaction = CompressedBytes(CompressionFormat.NONE, encodedTransaction)
        val signedTransaction =
            SignedTransaction(EncodingFormat.PROTOBUF, compressedTransaction, Signature(SIGNATURE_ALGO, "".toByteArray()))
        val hash = sha256(signedTransaction.encodeProto()).toHexString()

        assertEquals(stableStructureHash, hash, "Serialization change in Transaction - likely a breaking change")
    }

    @Test
    fun testTransactionRoundTripOC() {
        val personaId = persona.personaId
        val entityId = Id.ofData("entityId".toByteArray())
        val value = StringValue("value")
        val fact = Fact(personaId, entityId, CoreAttribute.Name.spec, value.asAnyValue(), Operation.Add)
        val transaction = Transaction.fromFacts(personaId, listOf(fact))
        val compressedTransaction = compress(CompressionFormat.NONE, Transaction.encode(transaction))
        val signature = signator.signBytes(transaction.authorityId.toString(),compressedTransaction.bytes )
        val signedTransaction = SignedTransaction(EncodingFormat.OC, compressedTransaction, signature)

        val encodedTransaction = ByteArrayOutputStream().use {
            SignedTransaction.encode(it, signedTransaction)
            it.toByteArray()
        }

        val decodedTransaction = ByteArrayInputStream(encodedTransaction).use {
            SignedTransaction.decode(it)
        }

        decodedTransaction.hasValidSignature(persona.publicKey)
        assertEquals(signedTransaction, decodedTransaction)
    }

    @Test
    fun testTransactionRoundTripProto() {
        val personaId = persona.personaId
        val entityId = Id.ofData("entityId".toByteArray())
        val value = StringValue("value")
        val fact = Fact(personaId, entityId, CoreAttribute.Name.spec, value.asAnyValue(), Operation.Add)
        val transaction = Transaction.fromFacts(personaId, listOf(fact))
        val compressedTransaction = compress(CompressionFormat.DEFLATE, Transaction.encode(transaction))
        val signature = signator.signBytes(transaction.authorityId.toString(),compressedTransaction.bytes )
        val signedTransaction = SignedTransaction(EncodingFormat.PROTOBUF, compressedTransaction, signature)

        val encodedTransaction = signedTransaction.encodeProto()
        val decodedTransaction = SignedTransaction.decodeProto(encodedTransaction)

        decodedTransaction.hasValidSignature(persona.publicKey)
        assertEquals(signedTransaction, decodedTransaction)
    }
}