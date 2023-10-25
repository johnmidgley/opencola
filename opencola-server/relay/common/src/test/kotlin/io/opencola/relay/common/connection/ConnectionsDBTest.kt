package io.opencola.relay.common.connection

import io.opencola.model.Id
import io.opencola.storage.newSQLiteDB
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConnectionsDBTest {
    private val database = newSQLiteDB("testAddConnectionTwice")

    @Test
    fun testCrudWithDuplicateAdd() {
        val connectionsDB = ConnectionsDB(database)
        val id = Id.new()
        val address0 = URI("http://localhost:8080")
        val address1 = URI("http://localhost:8081")
        val connectTimeMilliseconds = System.currentTimeMillis()
        connectionsDB.addConnection(id, address0, 0)

        val entry0 = connectionsDB.getConnection(id)
        assertNotNull(entry0)
        assertNull(entry0.connection)
        assertEquals(address0, entry0.address)
        assertEquals(0, entry0.connectTimeMilliseconds)

        connectionsDB.addConnection(id, address1, connectTimeMilliseconds)
        val entry1 = connectionsDB.getConnection(id)
        assertNotNull(entry1)
        assertNull(entry1.connection)
        assertEquals(address1, entry1.address)
        assertEquals(connectTimeMilliseconds, entry1.connectTimeMilliseconds)

        connectionsDB.deleteConnection(id)
        assertNull(connectionsDB.getConnection(id))
    }
}