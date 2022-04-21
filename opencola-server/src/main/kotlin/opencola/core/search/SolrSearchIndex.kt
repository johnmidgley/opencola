package opencola.core.search

import com.lordcodes.turtle.shellRun
import mu.KotlinLogging
import opencola.core.config.SearchConfig
import opencola.core.extensions.logErrorAndThrow
import opencola.core.extensions.nullOrElse
import opencola.core.extensions.toHexString
import opencola.core.model.CoreAttribute
import opencola.core.model.Entity
import opencola.core.model.Id
import opencola.core.security.sha256
import opencola.core.serialization.ByteArrayCodec
import opencola.core.serialization.StringByteArrayCodec
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.request.CoreAdminRequest
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.common.params.MapSolrParams
import org.apache.solr.common.util.NamedList
import java.io.File

// https://solr.apache.org/guide/8_10/using-solrj.html
// TODO: Create local objects for search results (see https://solr.apache.org/guide/8_10/using-solrj.html#java-object-binding)

class SolrSearchIndex(val authorityId: Id, val config: SearchConfig) : SearchIndex {
    private val logger = KotlinLogging.logger("SolrSearchIndex")

    private val solrClient: HttpSolrClient = HttpSolrClient.Builder(config.solr.baseUrl)
        .withConnectionTimeout(config.solr.connectionTimeoutMillis)
        .withSocketTimeout(config.solr.socketTimeoutMillis)
        .build()

    private val networkPath = File(System.getProperties()["user.dir"].toString(), "../network")
    private val configSet = "opencola"
    private val solrCollectionName = "$authorityId.$configSet"

    // This needs to come after networkPath has been initialized
    init{
        logger.info { "Initializing Solr Index: $solrCollectionName" }
        create()
    }

    private fun isCoreReady() : Boolean {
        return try {
            CoreAdminRequest.getStatus(
                solrCollectionName,
                solrClient
            ).coreStatus[solrCollectionName].get("dataDir") != null
        }catch(e: Exception){
            false
        }
    }

    private fun waitUntilCoreReady(pollIntervalInMilliseconds: Long = 300, maxRetries: Int = 20) {
        var attempt = 0

        // Probably a nicer functional way to do this by creating a sequence of retries
        while(attempt < maxRetries){
            if(isCoreReady())
                return

            Thread.sleep(pollIntervalInMilliseconds)
            attempt++
        }

        throw RuntimeException("Unable to verify core is ready")
    }
    override fun create() {
        // Shell docs at: https://github.com/lordcodes/turtle
        // TODO - error checking
        // TODO - Check that solr exists and that authorities directory exists (create if not)

        if(!isCoreReady()) {
            logger.info { "Creating Index: $solrCollectionName" }
            val createRequest = CoreAdminRequest.Create()
            createRequest.setCoreName(solrCollectionName)
            createRequest.configSet = configSet
            // TODO: Fix this warning - https://stackoverflow.com/questions/36569421/kotlin-how-to-work-with-list-casts-unchecked-cast-kotlin-collections-listkot
            val status = (solrClient.request(createRequest)["responseHeader"] as NamedList<Int>).get("status")

            if(status != 0){
                logger.logErrorAndThrow("Index creation failed with status $status")
            }

            waitUntilCoreReady()
        }
    }

    override fun destroy(){
        logger.info { "Deleting Index: $solrCollectionName" }

        CoreAdminRequest.unloadCore(solrCollectionName, solrClient)

        // TODO: Check result. Create a command function that logs the command
        shellRun(networkPath) {
            // The -T flag is needed, otherwise you could get the error "the input device is not a TTY"
            command("docker-compose", listOf("exec", "-T", "solr", "rm", "-rf", "/var/solr/data/$solrCollectionName"))
        }
    }

    // Currently, only searches name field. Will be fixed when schema is set up right
    // Should use Dismax parser: https://solr.apache.org/guide/8_10/the-dismax-query-parser.html
    // Highlighting: https://solr.apache.org/guide/8_10/highlighting.html
    // Spell checking: https://solr.apache.org/guide/8_10/spell-checking.html
    // Suggester: https://solr.apache.org/guide/8_10/suggester.html
    // More like this: https://solr.apache.org/guide/8_10/morelikethis.html (Likely better with phrase vector matching)
    override fun search(query: String): List<SearchResult> {
        logger.info { "Searching: $query" }

        val queryResponse = solrClient.query(
            solrCollectionName,
            MapSolrParams(mapOf("q" to getLuceneQueryString(query), "fl" to "id, authorityId, entityId, name, description"))
        )

        return queryResponse.results.map{
            SearchResult(
                Id.fromHexString(it.getFieldValue("authorityId").toString()),
                Id.fromHexString(it.getFieldValue("entityId").toString()),
                it.getFieldValue(CoreAttribute.Name.spec.name)?.toString(),
                it.getFieldValue(CoreAttribute.Description.spec.name)?.toString(),
            )
        }.toList()
    }

    private fun getDocId(authorityId: Id, entityId: Id): String {
        return sha256("${authorityId}:${entityId}").toHexString()
    }

    // TODO - This is fine for now, but what happens when you index the same doc (i.e. ref.to) from another user?
    // Don't want to overwrite local reference. Should just add trust rank, etc.
    // Also think about adding "from" multi field, so peer ids can be stored too.
    // Consider external fields for personalized ranks: https://solr.apache.org/guide/8_10/working-with-external-files-and-processes.html
    override fun add(entity: Entity){
        val id = getDocId(entity.authorityId,entity.entityId)
        logger.info { "Indexing authorityId: ${entity.authorityId} entityId: ${entity.entityId}" }

        val doc = SolrInputDocument()
        doc.addField("id", id)
        doc.addField("authorityId", authorityId.toString())
        doc.addField("entityId", entity.entityId.toString())


        // TODO: Probably need to manage multivalued fields (like tags) differently
        CoreAttribute.values()
            .map{ it.spec }
            .filter { it.isIndexable }
            .map { Pair(it.name, entity.getValue(it.name).nullOrElse { v -> it.codec.decode(v.bytes) } ) }
            .filter { it.second != null }
            .forEach {
                val (name, value) = it
                doc.addField(name, value)
            }

        // TODO - check status of update, commit and log errors
        solrClient.add(solrCollectionName, doc)
        solrClient.commit(solrCollectionName)
    }

    override fun delete(authorityId: Id, entityId: Id) {
        // TODO: Check response and log errors
        solrClient.deleteById(getDocId(authorityId, entityId))
    }
}