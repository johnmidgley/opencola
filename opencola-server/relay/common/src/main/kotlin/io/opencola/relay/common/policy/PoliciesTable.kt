import io.opencola.model.Id
import io.opencola.relay.common.policy.Policy
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow
import java.nio.charset.Charset

object Policies : LongIdTable() {
    val authorityId = binary("authorityId", 32)
    val name = varchar("name", 255).uniqueIndex()
    val policy = blob("policy")
    val editTimeMilliseconds = long("editTimeMilliseconds")
}

class PolicyRow(private val resultRow: ResultRow) {
    val id: Long by lazy { resultRow[Policies.id].value }
    val authorityId: Id by lazy { Id(resultRow[Policies.authorityId]) }
    val name: String by lazy { resultRow[Policies.name] }
    val policy: Policy by lazy { Json.decodeFromString(resultRow[Policies.policy].bytes.toString(Charset.defaultCharset())) }
    val editTimeMilliseconds: Long by lazy { resultRow[Policies.editTimeMilliseconds] }
}