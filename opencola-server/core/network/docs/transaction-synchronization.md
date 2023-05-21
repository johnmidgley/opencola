# Transaction Synchronization

The document describes how transactions are synchronized between nodes.

## GetTransactionsMessage

When a node starts up, it processes any pending transactions available from
NetworkProviders. Once complete, the node sends a GetTransactionsMessage to
each peer, that contains the transaction id of the most recent transaction
received from the peer. The peer will respond with any transactions that were
missed while offline and that were not available as pending transactions.

## PutTransactionMessage

When a node creates a new transaction, it is sent in a ```PutTransactionMessage```
to all peers. Transactions are sent as individual messages, not as a batch, for
caching / identification purposes.

When a node receives a ```PutTransactionMessage``` from a peer, it first stores the 
transaction in its transaction file store. As it may have been received out of
order, it might not immediately fit into the peer's transaction tree, but it 
will be needed at some point. The transaction is then checked to see if it is 
the next expected one:

- If the transaction fits, it's added. The next transaction id is then generated 
(it's the hash of the most recent transaction data) and looked up in the
store. If it exists, it means we received it out of order and are now ready to
add it, so we do. This process repeats until we can't find the next transaction.
- If it doesn't fit, it is left cached in the file store for future use

The other use of a ```PutTransactionMessage``` is in response to a 
```GetTransactionsMessage``` message. In this case, a node will send a sequence of 
```PutTransactionMessage```s. All but the last in the sequence will not contain
a ```lastTransactionId```, which indicates that no new transactions should 
be requested (otherwise we could end up in a situation where the same 
transaction is sent multiple times unnecessarily). The last transaction in the
sequence will contain the ```lastTransactionId``` which tells the receiver that
it is appropriate to request more transactions (unless the 
```lastTransactionId``` matches the last transaction id stored). 

