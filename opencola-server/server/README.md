<img src="../../img/pull-tab.svg" width="150" />

# server

This directory contains the server that supports the collaboration tool. It is built using [Ktor](https://ktor.io/]). The main points of interest are:

|File|Description|
|----|-----------|
|[`Application.kt`](./src/main/kotlin/opencola/server/Application.kt)| The entry point for the server.|
|[`Routing.kt`](./src/main/kotlin/opencola/server/plugins/routing/Routing.kt)|Routing table for API endpoints.|
|[`Feed.kt`](./src/main/kotlin/opencola/server/handlers/Feed.kt)|Where the feed is generated (start in `handleGetFeed()`.|

To start the server for development, you can run the server in your IDE (IntelliJ was used for writing the code) or you can build and run it directly from the command line following the [install](../../install/README.md) instructions.

The server stores files in an OS dependent manner:

|OS|Location|
|--|--------|
|MacOS| `~/Library/Application Support/OpenCola/storage`|
|Windows| `$home\AppData\Local\OpenCola\storage`|
|Linux| `~/.opencola/storage`|

The storage directory has the following files / directories:

|Entry|Description|
|-----|-----------|
|`address-book`|Directory containing the address book for the user, which includes information about peers. |
|`cert`| Directory of certificate related files. |
|`entity-store`| Directory containing the entity store for the user. |
|`event-bus.db`| Event bus database for resilient processing of events (i.e. used to ensure that events are processed even if the server is stopped in the middle of processing.|
|`filestore`| Directory containing files that are referred to by the entity store. All file "names" are base58 encoded hashes of some sort, generally the hash of the content. To control directoy size, the first 2 characters of the name are used as a top level directoy.|
|`keystore.pks`| File containing persona private keys.|
|`lucene`| Directory of search index files. |
|`opencola-server.yaml`| Server configuration file. |
|`web`| Files stored by the web application (settings & themes) |


The Web UI that interacts with this server is [`web-ui`](../../web-ui/README.md).
