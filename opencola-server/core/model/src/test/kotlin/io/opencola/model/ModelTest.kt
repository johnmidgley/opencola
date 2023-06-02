package io.opencola.model

import io.opencola.model.value.IdValue
import io.opencola.model.value.StringValue
import io.opencola.model.value.Value
import io.opencola.model.value.emptyValue
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals

class ModelTest {
    @Test
    fun testTransactionFactEmptyValue() {
        val fact = TransactionFact(CoreAttribute.Type.spec, emptyValue, Operation.Add)
        val encoded = TransactionFact.encode(fact)
        val decoded = TransactionFact.decode(encoded)
        assertEquals(fact, decoded)
    }

    @Test
    fun testTransactionFactStringValue() {
        val fact = TransactionFact(CoreAttribute.Type.spec, StringValue("type").asAnyValue(), Operation.Add)
        val encoded = TransactionFact.encode(fact)
        val decoded = TransactionFact.decode(encoded)
        assertEquals(fact, decoded)
    }

    @Test
    fun testTransactionFactIdValue() {
        val fact = TransactionFact(CoreAttribute.DataIds.spec, IdValue(Id.ofData("data".toByteArray())).asAnyValue(), Operation.Add)
        val encoded = TransactionFact.encode(fact)
        val decoded = TransactionFact.decode(encoded)
        assertEquals(fact, decoded)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testUnknownAttribute() {
        val unknownAttribute = Attribute(
            URI("opencola://attribute/unknown"),
            AttributeType.SingleValue,
            CoreAttribute.Name.spec.valueWrapper,
            false,
            null
        )
        val transactionFact = TransactionFact(unknownAttribute, StringValue("") as Value<Any>, Operation.Add)
        val transactionEntity = TransactionEntity(Id.new(), listOf(transactionFact))
        val transaction = Transaction(Id.new(), Id.new(), listOf(transactionEntity))
        val transactionProto = transaction.toProto()
        val transaction1 = Transaction.fromProto(transactionProto)

        assert(transaction1.transactionEntities.single().facts.isEmpty())
    }

}