package io.opencola.tools.export

import io.opencola.model.Id
import io.opencola.model.SignedTransaction
import io.opencola.storage.entitystore.EntityStore.TransactionOrder
import mu.KotlinLogging
import java.security.PublicKey

private val logger = KotlinLogging.logger("ChainVerifier")

data class ChainVerificationResult(
    val authorityId: String,
    val authorityName: String,
    val totalTransactions: Int,
    val validSignatures: Int,
    val invalidSignatures: Int,
    val chainIntact: Boolean,
    val firstBrokenLink: Int?,
    val issues: List<String>,
) {
    val isValid get() = invalidSignatures == 0 && chainIntact
}

/**
 * Verifies the transaction chain for a given authority.
 *
 * The chain is formed by:
 * - First transaction ID = SHA256("{authorityId}.firstTransaction")
 * - Subsequent IDs = SHA256(encodeProto(previousSignedTransaction))
 *
 * Each transaction's signature is verified against the authority's public key.
 */
class ChainVerifier(private val storage: StorageAccess) {

    fun verify(authorityId: Id, authorityName: String): ChainVerificationResult {
        val issues = mutableListOf<String>()

        val publicKey: PublicKey? = try {
            storage.getPublicKey(authorityId)
        } catch (e: Exception) {
            issues.add("Could not retrieve public key for authority: ${e.message}")
            null
        }

        // Get all transactions in ID-ascending order (chain order)
        val transactions = storage.entityStore.getSignedTransactions(
            setOf(authorityId),
            null,
            TransactionOrder.IdAscending,
            Int.MAX_VALUE
        ).toList()

        if (transactions.isEmpty()) {
            return ChainVerificationResult(
                authorityId = authorityId.toString(),
                authorityName = authorityName,
                totalTransactions = 0,
                validSignatures = 0,
                invalidSignatures = 0,
                chainIntact = true,
                firstBrokenLink = null,
                issues = issues + "No transactions found for this authority"
            )
        }

        var validSignatures = 0
        var invalidSignatures = 0
        var chainIntact = true
        var firstBrokenLink: Int? = null

        // Verify first transaction ID
        val expectedFirstId = Id.ofData("$authorityId.firstTransaction".toByteArray())
        if (transactions.first().transaction.id != expectedFirstId) {
            issues.add("First transaction ID mismatch. Expected: $expectedFirstId, Got: ${transactions.first().transaction.id}")
            chainIntact = false
            firstBrokenLink = 0
        }

        // Walk the chain
        var expectedNextId = expectedFirstId
        for ((index, signedTransaction) in transactions.withIndex()) {
            val tx = signedTransaction.transaction

            // Check transaction ID matches expected
            if (tx.id != expectedNextId) {
                if (chainIntact) {
                    chainIntact = false
                    firstBrokenLink = index
                }
                issues.add("Transaction $index: ID mismatch. Expected: $expectedNextId, Got: ${tx.id}")
            }

            // Verify signature
            if (publicKey != null) {
                try {
                    if (signedTransaction.hasValidSignature(publicKey)) {
                        validSignatures++
                    } else {
                        invalidSignatures++
                        issues.add("Transaction $index (${tx.id}): Invalid signature")
                    }
                } catch (e: Exception) {
                    invalidSignatures++
                    issues.add("Transaction $index (${tx.id}): Signature verification error: ${e.message}")
                }
            }

            // Compute next expected ID = SHA256(encodeProto(signedTransaction))
            try {
                expectedNextId = Id.ofData(signedTransaction.encodeProto())
            } catch (e: Exception) {
                issues.add("Transaction $index (${tx.id}): Failed to encode for next ID computation: ${e.message}")
                // Can't verify subsequent chain links
                if (chainIntact && index < transactions.size - 1) {
                    chainIntact = false
                    firstBrokenLink = index + 1
                }
                break
            }
        }

        logger.info { "Verified ${transactions.size} transactions for $authorityName ($authorityId): valid=$validSignatures, invalid=$invalidSignatures, chainIntact=$chainIntact" }

        return ChainVerificationResult(
            authorityId = authorityId.toString(),
            authorityName = authorityName,
            totalTransactions = transactions.size,
            validSignatures = validSignatures,
            invalidSignatures = invalidSignatures,
            chainIntact = chainIntact,
            firstBrokenLink = firstBrokenLink,
            issues = issues
        )
    }
}
