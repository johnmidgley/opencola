package io.opencola.model.protobuf

import io.opencola.application.TestApplication
import io.opencola.model.ResourceEntity
import io.opencola.model.SignedTransaction
import io.opencola.storage.ExposedEntityStoreContext
import io.opencola.storage.addPersona
import io.opencola.util.compress
import org.junit.Test
import java.net.URI

import kotlin.test.assertEquals


class ModelTest {
    @Test
    fun testProtobufSerialization() {
        val context = ExposedEntityStoreContext(TestApplication.getTmpDirectory(".storage"))
        val persona = context.addressBook.addPersona("Persona0", false)
        val entity0 = ResourceEntity(persona.personaId, URI("mock://entity0"), "entity0", "description0", "text0")
        val entity1 = ResourceEntity(persona.personaId, URI("mock://entity1"), "entity1", "description1", "text1")
        val signedTransaction = context.entityStore.updateEntities(entity0, entity1)!!

        val encoded = SignedTransaction.encode(signedTransaction)
        val compressedEncoded = compress(encoded)

        val protoPacked = SignedTransaction.toProto(signedTransaction)
        val protoCompressed = compress(protoPacked)
        val protoUnpacked = SignedTransaction.fromProto(protoPacked)
        assertEquals(signedTransaction, protoUnpacked)

        println("encoded: ${encoded.size} bytes")
        println("compressedEncoded: ${compressedEncoded.size} bytes")
        println("protoPacked: ${protoPacked.size} bytes")
        println("protoCompressed: ${protoCompressed.size} bytes")


    }
}