package opencola.core.model

import opencola.core.TestApplication
import opencola.core.extensions.toHexString
import opencola.core.security.Signator
import opencola.core.security.sha256
import org.junit.Test
import org.kodein.di.instance
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class TransactionTest {
    private val app = TestApplication.instance
    private val authority by app.injector.instance<Authority>()
    private val signator by app.injector.instance<Signator>()

    @Test
    fun testTransactionStructure(){
        val stableStructureHash = "be5d76bb8869a6ad3739da6b9b5889e050a32ffa1f3d0da78b4bcdd0b7071394"
        val id = Id.ofData("".toByteArray())
        val transactionFact = Transaction.TransactionFact(id, CoreAttribute.Type.spec, Value.emptyValue, Operation.Add)
        val transaction = Transaction(id, id, listOf(transactionFact))
        val signedTransaction = SignedTransaction(transaction, "".toByteArray())

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
        val transaction = Transaction(authorityId, authorityId, listOf(Transaction.TransactionFact.fromFact(fact)))
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