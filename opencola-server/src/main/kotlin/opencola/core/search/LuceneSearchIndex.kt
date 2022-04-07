package opencola.core.search

import mu.KotlinLogging
import opencola.core.extensions.nullOrElse
import opencola.core.extensions.recursiveDelete
import opencola.core.extensions.toHexString
import opencola.core.model.CoreAttribute.values
import opencola.core.model.Entity
import opencola.core.model.Id
import opencola.core.security.sha256
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path

class LuceneSearchIndex(val authorityId: Id, private val storagePath: Path) : SearchIndex {
    private val logger = KotlinLogging.logger("LuceneSearchIndex")
    private val analyzer = StandardAnalyzer()

    // TODO: How to close this?
    private val directory = FSDirectory.open(storagePath)

    init{
        logger.info { "Initializing Lucene Index" }
        create()
    }

    override fun create() {
        // Create a dummy doc to make sure index is ready to be searched
        val doc = Document()
        doc.add(Field("id", authorityId.toString(), StringField.TYPE_NOT_STORED))
        indexDocuments(listOf(doc))
    }

    override fun delete() {
        storagePath.recursiveDelete()
    }

    private fun indexDocuments(documents: Iterable<Document>){
        val indexWriterConfig = IndexWriterConfig(analyzer)
        indexWriterConfig.openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND

        IndexWriter(directory, IndexWriterConfig(analyzer)).use { writer ->
            documents.forEach {
                // Delete any existing document
                writer.deleteDocuments(QueryParser("id", analyzer).parse(it.get("id")))
                writer.addDocument(it)
            }
        }
    }
    // TODO: Make var-arg entity, so multiple docs can be indexed at once
    override fun index(entity: Entity) {
        logger.info { "Indexing authorityId: ${entity.authorityId} entityId: ${entity.entityId}" }
        val id = sha256("${entity.authorityId}:${entity.entityId}")
        val document = Document()
        document.add(Field("id", id.toHexString(), StringField.TYPE_STORED))
        document.add(Field("authorityId", entity.authorityId.toString(), StringField.TYPE_STORED))
        document.add(Field("entityId", entity.entityId.toString(), StringField.TYPE_STORED))

        // TODO: Probably need to manage multivalued fields (like tags) differently
        values()
            .map{ it.spec }
            .filter { it.isIndexable }
            .map { Pair(it.name, entity.getValue(it.name).nullOrElse { v -> it.codec.decode(v.bytes) } ) }
            .filter { it.second != null }
            .forEach {
                // TODO: This doesn't work well for non string types - create typed dispatcher
                val (name, value) = it
                document.add(Field(name, value.toString(), TextField.TYPE_STORED))
            }

        indexDocuments(listOf(document))
    }

    override fun search(query: String): List<SearchResult> {
        logger.info { "Searching: $query" }

        // TODO: This should probably be opened just once
        DirectoryReader.open(directory).use { directoryReader ->
            val indexSearcher = IndexSearcher(directoryReader)
            val parser = QueryParser("text", analyzer)
            val luceneQuery: Query = parser.parse(getLuceneQueryString(query))
            val scoreDocs = indexSearcher.search(luceneQuery, 100).scoreDocs

            logger.info{ "Found ${scoreDocs.size} results"}

            return scoreDocs.map {
                val document = indexSearcher.doc(it.doc)
                val authorityId = Id.fromHexString(document.get("authorityId"))
                val entityId = Id.fromHexString(document.get("entityId"))
                val name = document.get("name")
                val description = document.get("description")
                SearchResult(authorityId, entityId, name, description)
            }
        }
    }
}