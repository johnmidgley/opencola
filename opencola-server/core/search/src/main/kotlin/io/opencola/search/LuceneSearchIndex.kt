package io.opencola.search

import mu.KotlinLogging
import io.opencola.io.recursiveDelete
import io.opencola.model.CoreAttribute.values
import io.opencola.model.Entity
import io.opencola.model.Id
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
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
import java.io.Closeable
import java.nio.file.Path
import java.util.*

class LuceneSearchIndex(private val storagePath: Path) : AbstractSearchIndex(), Closeable {
    private val logger = KotlinLogging.logger("LuceneSearchIndex")
    private val analyzer = KeywordAnalyzer().let {
        PerFieldAnalyzerWrapper(StandardAnalyzer(), mapOf("authorityId" to it, "entityId" to it, "id" to it))
    }

    private val directory = FSDirectory.open(storagePath)

    init{
        logger.info { "Initializing Lucene Index" }
        create()
    }

    override fun create() {
        // Create a dummy doc to make sure index is ready to be searched
        val doc = Document()
        doc.add(Field("id", UUID.randomUUID().toString(), StringField.TYPE_NOT_STORED))
        indexDocuments(listOf(doc))
    }

    override fun destroy() {
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

            writer.flush()
        }
    }

    fun forceMerge() {
        IndexWriter(directory, IndexWriterConfig(analyzer)).use { writer ->
            writer.forceMerge(1)
        }
    }

    // TODO: Make var-arg entity, so multiple docs can be indexed at once
    override fun add(entity: Entity) {
        logger.info { "Indexing authorityId: ${entity.authorityId} entityId: ${entity.entityId}" }
        val id = getDocId(entity.authorityId, entity.entityId)
        val document = Document()
        document.add(Field("id", id, StringField.TYPE_STORED))
        document.add(Field("authorityId", entity.authorityId.toString(), StringField.TYPE_STORED))
        document.add(Field("entityId", entity.entityId.toString(), StringField.TYPE_STORED))

        // TODO: Probably need to manage multivalued fields (like tags) differently
        // TODO: Check out Google Sentence Piece: https://github.com/levyfan/sentencepiece-jni
        values()
            .map{ it.spec }
            .filter { it.isIndexable }
            .map { Pair(it.name, getAttributeAsText(entity, it)) }
            .filter { it.second != null && it.second!!.isNotBlank() }
            .forEach {
                // TODO: This doesn't work well for non string types - create typed dispatcher
                val (name, value) = it
                document.add(Field(name, value, TextField.TYPE_STORED))
            }

        indexDocuments(listOf(document))
        logger.info { "Indexed authorityId: ${entity.authorityId} entityId: ${entity.entityId}" }
    }

    override fun delete(authorityId: Id, entityId: Id) {
        val indexWriterConfig = IndexWriterConfig(analyzer)
        indexWriterConfig.openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND

        IndexWriter(directory, IndexWriterConfig(analyzer)).use{ writer ->
            val id = getDocId(authorityId, entityId)
            writer.deleteDocuments(QueryParser("id", analyzer).parse(id))
        }
    }

    override fun search(authorityIds: Set<Id>, query: String, maxResults: Int): List<SearchResult> {
        logger.info { "Searching: $query" }

        // TODO: This should probably be opened just once
        DirectoryReader.open(directory).use { directoryReader ->
            val indexSearcher = IndexSearcher(directoryReader)
            val parser = QueryParser("text", analyzer)
            val luceneQuery: Query = parser.parse(getLuceneQueryString(authorityIds, query))
            val scoreDocs = indexSearcher.search(luceneQuery, maxResults).scoreDocs

            logger.info{ "Found ${scoreDocs.size} results"}

            return scoreDocs.map {
                val document = indexSearcher.doc(it.doc)
                val authorityId = Id.decode(document.get("authorityId"))
                val entityId = Id.decode(document.get("entityId"))
                val name = document.get("name")
                val description = document.get("description")
                SearchResult(authorityId, entityId, name, description)
            }
        }
    }

    override fun close() {
        directory.close()
    }
}