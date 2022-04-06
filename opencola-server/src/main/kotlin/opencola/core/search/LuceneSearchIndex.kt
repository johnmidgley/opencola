package opencola.core.search

import mu.KotlinLogging
import opencola.core.extensions.nullOrElse
import opencola.core.extensions.toHexString
import opencola.core.model.CoreAttribute
import opencola.core.model.CoreAttribute.*
import opencola.core.model.Entity
import opencola.core.model.Id
import opencola.core.security.sha256
import opencola.core.serialization.ByteArrayCodec
import opencola.core.serialization.StringByteArrayCodec
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
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path
import kotlin.io.path.createDirectory


class LuceneSearchIndex(val authorityId: Id, val storagePath: Path) : SearchIndex {
    private val logger = KotlinLogging.logger("LuceneSearchIndex")
    private val analyzer = StandardAnalyzer()
    private val indexWriterConfig = IndexWriterConfig(analyzer)
    private val directory: Directory = FSDirectory.open(storagePath)

    init{
        logger.info { "Initializing Lucene Index" }
        indexWriterConfig.openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
    }

    override fun create() {
        // Will get created on index
        storagePath.createDirectory()
    }

    override fun delete() {
        TODO("Not yet implemented")
    }

    // TODO: Make var-arg entity, so multiple docs can be indexed at once
    override fun index(entity: Entity) {
        logger.info { "Indexing authorityId: ${entity.authorityId} entityId: ${entity.entityId}" }
        val id = sha256("${entity.authorityId}:${entity.entityId}")
        val doc = Document()
        doc.add(Field("id", id.toHexString(), StringField.TYPE_STORED))
        doc.add(Field("authorityId", entity.authorityId.toString(), StringField.TYPE_STORED))
        doc.add(Field("entityId", entity.entityId.toString(), StringField.TYPE_STORED))

        // TODO: Probably need to manage multivalued fields (like tags) differently
        values()
            .map{ it.spec }
            .filter { it.isIndexable }
            .map { Pair(it.name, entity.getValue(it.name).nullOrElse { v -> it.codec.decode(v.bytes) } ) }
            .filter { it.second != null }
            .forEach {
                // TODO: This doesn't work well for non string types - create typed dispatcher
                val (name, value) = it
                doc.add(Field(name, value.toString(), TextField.TYPE_STORED))
            }

        IndexWriter(directory, indexWriterConfig).use { writer ->
            writer.addDocument(doc)
        }
    }

    override fun search(query: String): List<SearchResult> {
        // TODO:
        val expandedQuery = CoreAttribute.values()
            .map { it.spec }
            // TODO: Fix this hack that identifies text search fields
            .filter { it.isIndexable && it.codec == StringByteArrayCodec as ByteArrayCodec<Any> }
            .joinToString(" ") { "${it.name}:\"$query\"" }

        // TODO: This should probably be opened just once
        DirectoryReader.open(directory).use {
            val indexSearcher = IndexSearcher(it)
            val parser = QueryParser("text", analyzer)
            val luceneQuery: Query = parser.parse(expandedQuery)
            val scoreDocs = indexSearcher.search(luceneQuery, 100).scoreDocs

            return scoreDocs.map {
                val doc = indexSearcher.doc(it.doc)
                val authorityId = Id.fromHexString(doc.get("authorityId"))
                val entityId = Id.fromHexString(doc.get("entityId"))
                val name = doc.get("name")
                val description = doc.get("description")
                SearchResult(authorityId, entityId, name, description)
            }
        }
    }
}