<img src="../../../img/pull-tab.svg" width="150" />

# storage

This library contains code that manages persisting information in a durable manner. OpenCola stores information in an OS dependent directory:

|OS|Location|
|--|--------|
|Linux| ```~/.opencola/storage/```|
|MacOS| ```~/Library/Application Support/OpenCola/storage/```|
|Windows| ```~\AppData\Local\OpenCola\storage```|

## addressbook

An [```AddressBook```](./AddressBook.kt) contains information about different personas and peers. It is intended to be isolated from the general entity store so that no infomration is unintentionally leaked. It has the following interface:

```kotlin
interface AddressBook : PublicKeyProvider<Id> {
    fun addUpdateHandler(handler: (AddressBookEntry?, AddressBookEntry?) -> Unit)
    fun removeUpdateHandler(handler: (AddressBookEntry?, AddressBookEntry?) -> Unit)
    fun updateEntry(
        entry: AddressBookEntry,
        suppressUpdateHandler: ((AddressBookEntry?, AddressBookEntry?) -> Unit)? = null
    ): AddressBookEntry
    fun getEntry(personaId: Id, id: Id) : AddressBookEntry?
    fun getEntries() : List<AddressBookEntry>
    fun deleteEntry(personaId: Id, entityId: Id)
}
```

The current ```AddressBook``` implemenation is an ```EntityStore``` ([see below](#entitystore))

There are two different types of address book entries. The first is ```AddressBookEntry```, which is used to store information about peers.

```kotlin
open class AddressBookEntry {
    // Id of the persona the entry is associated with
    val personaId: Id

    // Id of the authority the entry is describing - can be same as personaId then describing self
    val entityId: Id

    // Name of the authority
    val name: String

    // Public key of the auhtority
    val publicKey: PublicKey

    // Address of the authority (i.e. http or ocr address)
    val address: URI

    // Image of the authority
    val imageUri: URI?

    // Whehter the entity authority is active or not. For a persona, true means activity
    // is broadcast to associtated  peers and requests from associated peers are responded 
    // to, whereas for a peer, true means activity from the peer will be requested and 
    // accepted. False means no activity is transmitted, accepted or responded to.
    val isActive: Boolean 
}
```

The second is a ```PersonaAddressBookEntry```, which extends the first by adding a KeyPair and is used to store information about personas:

```kotlin
class PersonaAddressBookEntry : AddressBookEntry {
    // Used to sign and decrypt data for the persona
    val keyPair: KeyPair
}
```

## cache

The ```cache``` namespace containes code for caching data.

## MhtCache

An [```MhtCache```](./MhtCache.kt) is used to efficiently serve mhtml data to browsers, since it can't be served directly to a browser. It breaks an ```mhtml``` archive into parts allows access to individual parts.

## entitystore

An [```EntityStore```](./src/main/kotlin/io/opencola/storage/entitystore/EntityStore.kt) stores information about entities and is one of the core interfaces of OpenCola:

```kotlin
interface EntityStore {
    // Get all facts that that have an authorityId in authoritiyIds and an entityId in
    // entityIds
    fun getFacts(authorityIds: Set<Id>, entityIds: Set<Id>): List<Fact>

    // Update a set of entities. 
    fun updateEntities(vararg entities: Entity): SignedTransaction?

    // Get all entites that that have an authorityId in authoritiyIds and an entityId in
    // entityIds
    fun getEntities(authorityIds: Set<Id>, entityIds: Set<Id>): List<Entity>

    // Delete entites from an authrority
    fun deleteEntities(authorityId: Id, vararg entityIds: Id)

    // Add signed transactions. This is called when data is receieved from a peer.
    fun addSignedTransactions(signedTransactions: List<SignedTransaction>)

    // Get signed transactions for a set of authorities. Used to synchronize data with peers
    fun getSignedTransactions(
        authorityIds: Set<Id>,
        startTransactionId: Id?,
        order: TransactionOrder,
        limit: Int
    ): Iterable<SignedTransaction>
}
```

Currently [ExposedEntityStoreV2](./src/main/kotlin/io/opencola/storage/entitystore/ExposedEntityStoreV2.kt) (V1 is deprecated) is the only implementation of EntityStore. It is pretty general, though, as [Exposed](https://github.com/JetBrains/Exposed) is an ORM that allows for most major databases to be used as a backend (Maria, MySQL, Oracle, Postgres, SQL Server and SQLite). 

OpenCola orgianlly used Postgres, but switched to SQLite because its simpler to use (i.e. it runs in memory with the rest of the application) and available on mobile devices. 

TODO: DB format and file storage

## filestore

The ```filestore``` namespace contains code that stores files. There are two types of filestores used in OpenCola: content addressed and Id addressed. 

A [```ContentAddressFileStore```](./src/main/kotlin/io/opencola/storage/filestore/ContentAddressedFileStore.kt) is used to store the majority of files in OpenCola. It allows 

```kotlin
interface ContentAddressedFileStore {
    // Check if a file exists
    fun exists(dataId: Id) : Boolean

    // Enumerate all data in store by Id
    fun getDataIds() : Sequence<Id>

    // Get the input stream for data
    fun getInputStream(dataId: Id): InputStream?

    // Write data to the store (and compute its Id)
    fun write(inputStream: InputStream) : Id

    // Delete data from the store
    fun delete(dataId: Id)
}
```

The current implemenation, [```FileSystemContentAddressedFileStore```](./src/main/kotlin/io/opencola/storage/filestore/FileSystemContentAddressedFileStore.kt) is FileSystem based, but ideally you could use some sort of cloud storage if you wanted, with the possibility of sharing it with peers if you like. 

An [```IdAddressedFileStore```](./src/main/kotlin/io/opencola/storage/filestore/IdAddressedFileStore.kt) allows information to be stored directly by ```Id```, without the requirement that the ```Id``` is the ```Id``` (hash) of the underlying data. It is used to store transactions. 


