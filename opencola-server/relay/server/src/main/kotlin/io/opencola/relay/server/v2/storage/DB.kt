package io.opencola.relay.server.v2.storage

import org.jetbrains.exposed.dao.id.LongIdTable

class DB() {
    // TODO: Can these be broken up by service?

    private class Connections(name: String = "Connections") : LongIdTable(name) {
        // TODO: Anything else? Bytes Read? Bytes Written? Messages Sent? Messages Received? Last seen?
        val publicKey = binary("publicKey", 32).uniqueIndex()
        val connectTimeMilliseconds = long("connectTimeMilliseconds")
        val host = varchar("host", 255)
    }

    private class Messages(name: String = "Messages") : LongIdTable(name) {
        val timeMilliseconds = long("timeMilliseconds")
        val from = binary("from", 32).index()
        val to = binary("to", 32).index()
        val envelope = blob("envelope")
        val bodyDataId = binary("to", 32)
        val bodySize = long("bodySize")
    }

    private class UserPolicies(name: String = "UserPolicies") : LongIdTable(name) {
        val publicKey = binary("publicKey", 32).uniqueIndex()
        val policy = blob("policy")
    }
}