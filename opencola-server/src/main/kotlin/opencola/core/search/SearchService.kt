package opencola.core.search


import com.lordcodes.turtle.shellRun
import opencola.core.model.CoreAttribute
import opencola.core.model.Id
import opencola.core.model.Authority
import opencola.core.model.Entity
import mu.KotlinLogging
import opencola.core.extensions.nullOrElse
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.request.CoreAdminRequest
import org.apache.solr.client.solrj.response.UpdateResponse
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.common.params.MapSolrParams
import java.io.File


// TODO: Make client params configurable
private const val solrBaseUrl = "http://localhost:8983/solr/"
private const val solrConnectionTimeoutMillis = 10000
private const val solrSocketTimeoutMillis = 60000

// https://solr.apache.org/guide/8_10/using-solrj.html
// TODO: Create local objects for search results (see https://solr.apache.org/guide/8_10/using-solrj.html#java-object-binding)

// TODO: Probably just id is better. Nothing needs to be signed, and no other properties are used
class SearchService(val authority: Authority) {
    private val logger = KotlinLogging.logger {}

    private val solrClient: HttpSolrClient = HttpSolrClient.Builder(solrBaseUrl)
        .withConnectionTimeout(solrConnectionTimeoutMillis)
        .withSocketTimeout(solrSocketTimeoutMillis)
        .build()

    private val networkPath = File(System.getProperties()["user.dir"].toString(), "../network")
    private val configSet = "opencola"
    private val solrCollectionName = authority.entityId.toString() + "." + configSet

    // This needs to come after networkPath has been initialized
    init{
        createIndex()
    }

    fun isCoreReady() : Boolean {
        try {
            return CoreAdminRequest.getStatus(
                solrCollectionName,
                solrClient
            ).coreStatus[solrCollectionName].get("dataDir") != null
        }catch(e: Exception){
            return false
        }
    }

    fun waitUntilCoreReady(pollIntervalInMilliseconds: Long, maxRetries: Int) : Boolean {
        var attempt = 0

        // Probably a nicer functional way to do this by creating a sequence of retries
        while(attempt < maxRetries){
            if(isCoreReady())
                return true

            Thread.sleep(pollIntervalInMilliseconds)
            attempt++
        }

        return false
    }
    fun createIndex() : Boolean{
        // Shell docs at: https://github.com/lordcodes/turtle
        // TODO - error checking
        // TODO - Check that solr exists and that authorities directory exists (create if not)

        var status = CoreAdminRequest.getStatus(solrCollectionName, solrClient)

        if(!isCoreReady()) {
            val createRequest = CoreAdminRequest.Create()
            createRequest.setCoreName(solrCollectionName)
            createRequest.configSet = configSet
            val res = solrClient.request(createRequest)

            return waitUntilCoreReady(300, 20)
        }

        return true
    }

    fun deleteIndex(){
        CoreAdminRequest.unloadCore(solrCollectionName, solrClient)
//        var result = shellRun(networkPath){
//            command("rm", listOf("-rf", "var-solr/data/$solrCollectionName"))
//        }

        var result = shellRun(networkPath){
            command("docker-compose", listOf("exec", "solr", "rm", "-rf", "/var/solr/data/$solrCollectionName"))
        }
    }

    // Currently, only searches name field. Will be fixed when schema is set up right
    // Should use Dismax parser: https://solr.apache.org/guide/8_10/the-dismax-query-parser.html
    // Highlighting: https://solr.apache.org/guide/8_10/highlighting.html
    // Spell checking: https://solr.apache.org/guide/8_10/spell-checking.html
    // Sugester: https://solr.apache.org/guide/8_10/suggester.html
    // More like this: https://solr.apache.org/guide/8_10/morelikethis.html (Likely better with phrase vector matching)
    fun search(query: String): List<SearchResult> {
        // Can do with without mutating using "to" operator

        // TODO: - Return Local search results, not solr objects
        val queryResponse = solrClient.query(
            solrCollectionName,
            MapSolrParams(mapOf("q" to "name:\"$query\" description:\"$query\"", "fl" to "id, name, description"))
        )

        return queryResponse.results.map{
            SearchResult(
                Id.fromHexString(it.getFieldValue("id").toString()),
                it.getFieldValue(CoreAttribute.Name.spec.name)?.toString(),
                it.getFieldValue(CoreAttribute.Description.spec.name)?.toString(),
            )
        }.toList()
    }

    // TODO - This is fine for now, but what happens when you index the same doc (i.e. ref.to) from another user?
    // Don't want to overwrite local reference. Should just add trust rank, etc.
    // Also think about adding "from" multi field, so peer ids can be stored too.
    // Consider external fields for personalized ranks: https://solr.apache.org/guide/8_10/working-with-external-files-and-processes.html
    fun index(entity: Entity){
        val doc = SolrInputDocument()
        doc.addField("id", entity.entityId.toString())

        // TODO: Probably need to manage multivalued fields (like tags) differently
        CoreAttribute.values()
            .map{ it.spec }
            .filter { it.isIndexable }
            .map { Pair(it.name, entity.getValue(it.name).nullOrElse { v -> it.codec.decode(v.bytes) } ) }
            .filter { it.second != null }
            .forEach {
                var (name, value) = it
                doc.addField(name, value)
            }

        // TODO - check status of update, commit and log errors
        val updateResponse: UpdateResponse = solrClient.add(solrCollectionName, doc)
        solrClient.commit(solrCollectionName)
    }
}