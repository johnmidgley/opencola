package opencola.service

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import opencola.core.content.HtmlParser
import opencola.core.content.MhtmlPage
import opencola.core.content.TextExtractor
import opencola.core.extensions.ifNotNullOrElse
import opencola.core.extensions.nullOrElse
import opencola.core.model.*
import opencola.core.model.Peer
import opencola.core.network.PeerRouter
import opencola.core.network.PeerRouter.Event
import opencola.core.network.PeerRouter.Notification
import opencola.core.network.PeerRouter.PeerStatus.Status.*
import opencola.core.search.SearchIndex
import opencola.core.storage.EntityStore
import opencola.core.storage.FileStore
import opencola.server.TransactionsResponse
import org.apache.james.mime4j.message.DefaultMessageWriter
import java.io.ByteArrayOutputStream

// TODO - with an event bus, this is likely not needed, as this just really coordinates messaging between components
class EntityService(val authority: Authority,
                    private val entityStore: EntityStore,
                    private val searchIndex: SearchIndex,
                    private val fileStore: FileStore,
                    private val peerRouter: PeerRouter,
                    private val textExtractor: TextExtractor) {
    private val logger = KotlinLogging.logger("EntityService")
    private val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

    init {
        updatePeerTransactions()
    }

    private fun updatePeerTransactions() {
        runBlocking {
            peerRouter.peers.forEach {
                async { requestTransactions(it) }
            }
        }
    }

    fun requestTransactions(peerId: Id){
        val peer = peerRouter.getPeer(peerId)
            ?: throw IllegalArgumentException("Attempt to request transactions for unknown peer: $peerId ")

        // TODO: This blocks startup. Make fully async (and/or handle startup with event bus)
        runBlocking {
            async { requestTransactions(peer) }
        }
    }

    private fun getTransactionsUrl(peer: Peer, peerTransactionId: Id?): String {
        return "http://${peer.host}/transactions/${peer.id}${peerTransactionId.ifNotNullOrElse({"/${it.toString()}"}, {""})}?peerId=${authority.authorityId}"
    }

    private suspend fun requestTransactions(peer: Peer){
        // TODO: Update getTransaction to take authorityId
        logger.info { "Requesting transaction from: ${peer.name}" }

        try {
            var peerTransactionId = entityStore.getLastTransactionId(peer.id)

            // TODO - Config max batches
            // TODO: Set reasonable max batches and batch sizes
            for(batch in 1..10) {
                val urlString = getTransactionsUrl(peer, peerTransactionId)
                logger.info { "Requesting transactions - Batch $batch - $urlString" }

                //TODO - see implement PeerService.get(peer, path) to get rid of httpClient here
                // plus no need to update peer status here
                val transactionsResponse: TransactionsResponse =
                    httpClient.get(urlString)

                peerRouter.updateStatus(peer.id, Online)
                entityStore.addSignedTransactions(transactionsResponse.transactions)
                transactionsResponse.transactions.forEach{ indexTransaction(it)}
                peerTransactionId = transactionsResponse.transactions.last().transaction.id

                if(transactionsResponse.transactions.isEmpty()
                    || peerTransactionId == transactionsResponse.currentTransactionId)
                    break
            }
        }
        catch(e: java.net.ConnectException){
            logger.info { "${peer.name} appears to be offline." }
            peerRouter.updateStatus(peer.id, Offline)
        }
        catch (e: Exception){
            logger.error { e.message }
            // TODO: This should depend on the error
            peerRouter.updateStatus(peer.id, Offline)
        }
        logger.info { "Completed requesting transaction transaction from: ${peer.name}" }
    }

    fun updateEntities(vararg entities: Entity): SignedTransaction? {
        val signedTransaction = entityStore.updateEntities(*entities)

        if (signedTransaction != null) {
            peerRouter.broadcastMessage(
                "notifications",
                Notification(signedTransaction.transaction.authorityId, Event.NewTransactions)
            )
        }

        return signedTransaction
    }

    fun updateResource(mhtmlPage: MhtmlPage, actions: Actions): ResourceEntity {
        // TODO: Add data id to resource entity - when indexing, index body from the dataEntity
        // TODO: Parse description
        // TODO - EntityStore should detect if a duplicate entity is added. Just merge it?
        val writer = DefaultMessageWriter()
        ByteArrayOutputStream().use { outputStream ->
            writer.writeMessage(mhtmlPage.message, outputStream)
            val pageBytes = outputStream.toByteArray()
            val dataId = fileStore.write(pageBytes)
            val mimeType = textExtractor.getType(pageBytes)
            val resourceId = Id.ofUri(mhtmlPage.uri)
            val entity = (entityStore.getEntity(authority.authorityId, resourceId) ?: ResourceEntity(
                authority.entityId,
                mhtmlPage.uri
            )) as ResourceEntity

            // Add / update fields
            entity.dataId = dataId
            entity.name = mhtmlPage.title

            if(mhtmlPage.htmlText != null){
                entity.text = textExtractor.getBody(mhtmlPage.htmlText.toByteArray())
                // TODO - Check if this writes a retraction when description is null
                val htmlParser = HtmlParser(mhtmlPage.htmlText)
                entity.description = htmlParser.parseDescription()
                entity.imageUri = htmlParser.parseImageUri()
            }

            actions.trust.nullOrElse { entity.trust = it }
            actions.like.nullOrElse { entity.like = it }
            actions.rating.nullOrElse { entity.rating = it }

            val dataEntity = (entityStore.getEntity(authority.authorityId, dataId) ?: DataEntity(
                authority.entityId,
                dataId,
                mimeType
            ))

            updateEntities(entity, dataEntity)
            searchIndex.index(entity)
            return entity
        }
    }

    private fun indexTransaction(signedTransaction: SignedTransaction) {
        val authorityId = signedTransaction.transaction.authorityId

        signedTransaction.transaction.transactionEntities
            .map { f -> f.entityId }
            .distinct()
            .forEach { eid ->
                val entity = entityStore.getEntity(authorityId, eid)

                if (entity == null)
                    logger.error { "Can't get entity after persisting entity facts: $authorityId:$eid" }
                else if (entity !is DataEntity){ // TODO: Should data entities be indexed?
                    //TODO: Archive will not be available - figure out what to do
                    // Call peer for data?
                    searchIndex.index(entity)
                }
            }
    }
}