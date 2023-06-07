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
        val stableStructureHash = "f2c880f7543f7307609d0d67b7e349798b705aab3a166989858fd5e50ad23467"
        val id = Id.ofData("".toByteArray())
        val fact = Fact(id, id, CoreAttribute.Type.spec, StringValue("").asAnyValue(), Operation.Add)
        val encodedTransaction = Transaction.fromFacts(id, listOf(fact), 0).encodeProto()
        val signedTransaction =
            SignedTransaction(EncodingFormat.PROTOBUF, encodedTransaction, Signature(SIGNATURE_ALGO, "".toByteArray()))
        val hash = sha256(signedTransaction.encodeProto()).toHexString()

        assertEquals(stableStructureHash, hash, "Serialization change in Transaction - likely a breaking change")
    }

    @Test
    fun testTransactionRoundTrip() {
        val personaId = persona.personaId
        val entityId = Id.ofData("entityId".toByteArray())
        val value = StringValue("value")
        val fact = Fact(personaId, entityId, CoreAttribute.Name.spec, value.asAnyValue(), Operation.Add)
        val transaction = Transaction.fromFacts(personaId, listOf(fact))
        val signature = signator.signBytes(transaction.authorityId.toString(), Transaction.encode(transaction))
        val signedTransaction = SignedTransaction(EncodingFormat.OC, transaction.encode(), Signature(signature.algorithm, signature.bytes))

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
}