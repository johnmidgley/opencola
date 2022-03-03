package opencola.model

import opencola.core.TestApplication
import opencola.core.extensions.toHexString
import opencola.core.model.*
import opencola.core.security.Signator
import opencola.core.security.sha256
import org.junit.Test
import org.kodein.di.instance
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class TransactionTest {
    private val app = TestApplication.init()
    private val authority by app.injector.instance<Authority>()
    private val signator by app.injector.instance<Signator>()

    @Test
    fun testTransactionStructure(){
        val stableStructureHash = "e55ebeffb35589fa168db5fed98e3dc02f065ffdf1f5206422a71d055a9f1cd1"
        val id = Id.ofData("".toByteArray())
        val transactionFact = Transaction.TransactionFact(id, CoreAttribute.Type.spec, Value.emptyValue, Operation.Add)
        val transaction = Transaction(0, id, listOf(transactionFact))
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
        val transaction = Transaction(0, authorityId, listOf(Transaction.TransactionFact.fromFact(fact)))
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