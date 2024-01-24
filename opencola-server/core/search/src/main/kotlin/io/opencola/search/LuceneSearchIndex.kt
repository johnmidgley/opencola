/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.search

import io.opencola.io.recursiveDelete
import io.opencola.model.CoreAttribute.values
import io.opencola.model.Entity
import io.opencola.model.Id
import io.opencola.util.Base58
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.store.FSDirectory
import java.io.Closeable
import java.nio.file.Path
import java.util.*

class LuceneSearchIndex(private val storagePath: Path) : AbstractSearchIndex(), Closeable {
    private val analyzer = KeywordAnalyzer().let {
        PerFieldAnalyzerWrapper(StandardAnalyzer(), mapOf("authorityId" to it, "entityId" to it, "id" to it))
    }

    private val directory = FSDirectory.open(storagePath)

    init{
        logger.info { "Initializing Lucene Index" }
        create()
        forceMerge()
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

            writer.commit()
        }
    }

    fun forceMerge() {
        IndexWriter(directory, IndexWriterConfig(analyzer)).use { writer ->
            writer.forceMerge(1)
        }
    }

    // TODO: Make var-arg entity, so multiple docs can be indexed at once
    override fun addEntities(vararg entities: Entity) {
        entities.forEach {logger.info { "Indexing authorityId: ${it.authorityId} entityId: ${it.entityId}" } }

        val documents = entities.map { entity ->
            val id = getDocId(entity.authorityId, entity.entityId)
            val document = Document()
            document.add(Field("id", id, StringField.TYPE_STORED))
            document.add(Field("authorityId", entity.authorityId.toString(), StringField.TYPE_STORED))
            document.add(Field("entityId", entity.entityId.toString(), StringField.TYPE_STORED))

            // TODO: Probably need to manage multivalued fields (like tags) differently
            // TODO: Check out Google Sentence Piece: https://github.com/levyfan/sentencepiece-jni
            values()
                .map { it.spec }
                .filter { it.isIndexable }
                .map { Pair(it.name, getAttributeAsText(entity, it)) }
                .filter { it.second != null && it.second!!.isNotBlank() }
                .forEach {
                    // TODO: This doesn't work well for non string types - create typed dispatcher
                    val (name, value) = it
                    document.add(Field(name, value, TextField.TYPE_STORED))
                }

            document
        }

        indexDocuments(documents)

        entities.forEach { logger.info { "Indexed authorityId: ${it.authorityId} entityId: ${it.entityId}" } }
    }

    override fun deleteEntities(authorityId: Id, vararg entityIds: Id) {
        val indexWriterConfig = IndexWriterConfig(analyzer)
        indexWriterConfig.openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND

        IndexWriter(directory, IndexWriterConfig(analyzer)).use{ writer ->
            entityIds.forEach {entityId ->
                val id = getDocId(authorityId, entityId)
                writer.deleteDocuments(QueryParser("id", analyzer).parse(id))
            }
        }
    }

    private fun getPagingToken(scoreDoc: ScoreDoc): String {
        return Base58.encode( "${scoreDoc.doc}:${scoreDoc.score}:${scoreDoc.shardIndex}".toByteArray())
    }

    private fun getScoreDoc(pagingToken: String): ScoreDoc {
        val (doc, score, shardIndex) = Base58.decode(pagingToken).toString(Charsets.UTF_8).split(":")
        return ScoreDoc(doc.toInt(), score.toFloat(), shardIndex.toInt())
    }

    override fun getResults(query: Query, maxResults: Int, pagingToken: String?): SearchResults {
        logger.info { "Searching: $query" }

        // TODO: This should probably be opened just once - also use memory mapped (ask Ivan)
        DirectoryReader.open(directory).use { directoryReader ->
            val indexSearcher = IndexSearcher(directoryReader)
            val parser = QueryParser("text", analyzer)
            val luceneQuery = parser.parse(getLuceneQueryString(query))
            val scoreDocs =
                (if (pagingToken == null)
                    indexSearcher.search(luceneQuery, maxResults)
                else
                    indexSearcher.searchAfter(getScoreDoc(pagingToken), luceneQuery, maxResults)).scoreDocs


            logger.info{ "Found ${scoreDocs.size} results"}
            val results = scoreDocs.map {
                val document = indexSearcher.storedFields().document(it.doc)
                val authorityId = Id.decode(document.get("authorityId"))
                val entityId = Id.decode(document.get("entityId"))
                val name = document.get("name")
                val description = document.get("description")
                SearchResult(authorityId, entityId, name, description)
            }

            val outPagingToken = if(scoreDocs.size == maxResults) getPagingToken(scoreDocs.last()) else null
            return SearchResults(outPagingToken, results)
        }
    }

    override fun close() {
        directory.close()
    }
}