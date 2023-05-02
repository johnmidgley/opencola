package io.opencola.model

import io.opencola.model.value.IdValue
import io.opencola.model.value.StringValue
import io.opencola.model.value.emptyValue
import org.junit.Test
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
        val fact = TransactionFact(CoreAttribute.DataId.spec, IdValue(Id.ofData("data".toByteArray())).asAnyValue(), Operation.Add)
        val encoded = TransactionFact.encode(fact)
        val decoded = TransactionFact.decode(encoded)
        assertEquals(fact, decoded)
    }

}