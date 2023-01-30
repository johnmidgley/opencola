package opencola.core.model

import io.opencola.model.*
import opencola.core.TestApplication
import io.opencola.util.toHexString
import io.opencola.security.SIGNATURE_ALGO
import io.opencola.security.Signator
import io.opencola.security.sha256
import io.opencola.storage.AddressBook
import org.junit.Test
import org.kodein.di.instance
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class TransactionTest {
    private val app = TestApplication.instance
    private val authority = app.inject<AddressBook>().getAuthorities().filterIsInstance<Persona>().first()
    private val signator by app.injector.instance<Signator>()

    @Test
    fun testTransactionStructure(){
        val stableStructureHash = "3960f25b533fedf4cda71a701553a4bf7fef02689a0b5bb7a335de3bd6c6efbd"
        val id = Id.ofData("".toByteArray())
        val fact = Fact(id, id, CoreAttribute.Type.spec, Value.emptyValue, Operation.Add)
        val transaction = Transaction.fromFacts(id, listOf(fact), 0)
        val signedTransaction = SignedTransaction(transaction, SIGNATURE_ALGO, "".toByteArray())

        val hash = ByteArrayOutputStream().use {
            SignedTransaction.encode(it, signedTransaction)
            sha256(it.toByteArray()).toHexString()
        }

        assertEquals(stableStructureHash, hash, "Serialization change in Transaction - likely a breaking change")
    }

    @Test
    fun testTransactionRoundTrip(){
        val authorityId = authority.authorityId
        val entityId = Id.ofData("entityId".toByteArray())
        val value = Value("value".toByteArray())
        val fact = Fact(authorityId, entityId, CoreAttribute.Name.spec, value, Operation.Add)
        val transaction = Transaction.fromFacts(authorityId, listOf(fact))
        val signedTransaction = transaction.sign(signator)

        val encodedTransaction = ByteArrayOutputStream().use {
            SignedTransaction.encode(it, signedTransaction)
            it.toByteArray()
        }

        val decodedTransaction = ByteArrayInputStream(encodedTransaction).use{
            SignedTransaction.decode(it)
        }

        decodedTransaction.isValidTransaction(authority.publicKey!!)
        assertEquals(signedTransaction, decodedTransaction)
    }
}