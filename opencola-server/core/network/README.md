<img src="../../../img/pull-tab.svg" width="150" />

# network

The network library contains code that allows instances of OpenCola to communicate with each other. 

## Network Providers
In order for peers to communicate, they need to know how to talk to each other, using some protocol which is implemented with a [NetworkProvider](./src/main/kotlin/io/opencola/network/providers/NetworkProvider.kt):

```kotlin
interface NetworkProvider {
    // Set an event handler that should be called for any events raised by the provider (e.g. NO_PENDING_MESSAGES). 
    fun setEventHandler(handler: EventHandler)

    // Set a message handler that is called whenever a message is received by the provider
    fun setMessageHandler(handler: MessageHandler)

    // Start the provider
    fun start(waitUntilReady: Boolean = false)

    // Stop the provider
    fun stop()

    // Get the scheme of URIs that the provider handlers (e.g. http or ocr)
    fun getScheme() : String

    // Validate an address that the provider should handle. Can check if the remote server is valid.
    fun validateAddress(address: URI)

    // Inform the provider tha a peer has been added, which may add a new remote connection.
    fun addPeer(peer: AddressBookEntry)

    // Inform the provider that a peer has been removed, which may remove a remote connection
    fun removePeer(peer: AddressBookEntry)

    // Send a message from a persona to a set of peers. 
    fun sendMessage(from: PersonaAddressBookEntry, to: Set<AddressBookEntry>, message: Message)
}
```

Currently there are two providers: 

### HTTP
 
 Nodes can communicate directly over [HTTP](./src/main/kotlin/io/opencola/network/providers/http/HttpNetworkProvider.kt), which requires that nodes can directly see each other (i.e. they are on the same physical network, vpn, etc.). While HTTP is used, to avoid having to negotiate certificates, all communication is encrypted using PKI (see the security library). 

 Communicating directly over HTTP minimizes intermediaries, thus maximizing privacy, but requires that a node and its peer need to be online at the same time in order to communicate.

 ### OC Relay

OpenCola also supports communication via a [relay](./src/main/kotlin/io/opencola/network/providers/relay/OCRelayNetworkProvider.kt). The relay is hosted on the open internet, so it is accessible to anybody that has access to the internet. 

### Other Possibilites

There are a number of other possibiiltes that we're considering for other network providers, including Veilid, Tor, shared / network file system based, email, etc.


## Network Node

The [NetworkNode](./src/main/kotlin/io/opencola/network/NetworkNode.kt) class is the central class for messaging;  it delegates to providers and handles messages from providers. It has a simple API:

```kotlin
class NetworkNode {
   // Add a provider that knows how to communicate over a specific protocol
  fun addProvider(provider: NetworkProvider)

  // Send a message from a persona to a set of peers
  fun sendMessage(from: PersonaAddressBookEntry, to: Set<AddressBookEntry>, message: Message) 
 
  // Send a message from a persona to all peers of the persona
  fun broadcastMessage(from: PersonaAddressBookEntry, message: Message)
}
```




## Messages

OpenCola makes use of a small set of message types:

|Message|Description|
|-------|-----------|
|[```PingMessage```](./src/main/kotlin/io/opencola/network/message/PingMessage.kt)|Ping a peer.|
|[```PongMessage```](./src/main/kotlin/io/opencola/network/message/PongMessage.kt.kt)|Respond to a ping.|
|[```GetDataMessage```](./src/main/kotlin/io/opencola/network/message/GetDataMessage.kt)|Request a data blob by Id. This is called when a node detects that some data is missing, for example when the feed is being genrated and an attachment for an item is missing.|
|[```PutDataMessage```](./src/main/kotlin/io/opencola/network/message/PutDataMessage.kt)|Send a data blob. This is called when a new transaction is created that has associated data (e.g. attachments) or in response to a ```GetDataMesssage```|
|[```GetTransactionsMessage```](./src/main/kotlin/io/opencola/network/message/GetTransactionsMessage.kt)|Request transactions. This message includes a current transaction Id from the sender so the receiver can figure out which transactions to send. This message also includes the current transaction Id from the sender so that the receiver can know if it needs to request any transactions itself.|
|[```PutTransactionMessage```](./src/main/kotlin/io/opencola/network/message/PutTransactionMessage.kt)|Send transactions to a peer. This is used when a new transaction is created or upon peer request.|