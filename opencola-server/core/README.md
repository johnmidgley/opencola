<img src="../../img/pull-tab.svg" width="150" alt="OpenCola" />

# Core

This directory contains libraries that are useful in building backend OpenCola tools and applictiations (i.e. things used by the CLI, application server and relay server). 

|Library|Description|
|-------|-----------|
|[`application`](./application)| Dependency injection, config, resources, etc.|
|[`content`](./content)| Content handling (mhtml, html, pdf, etc.)|
|[`event`](./event)| Event bus and event logging. |
|[`io`](./io)| Input / output |
|[`model`](./model)| OpenCola data model |
|[`network`](./network)|Peer to peer communication|
|[`search`](./search)| Search functionality |
|[`storage`](./storage)| Durable data storage |
|[`system`](./system)| System (e.g. OS) level functionality |
|[`util`](./util) | Miscellaneous utility code |
 
The most important interfaces to understand are [`EntityStore`](./opencola-server/core/storage/README.md#entitystore), [`ContentAddressedFileStore`](./opencola-server/core/storage/README.md#filestore), [`NetworkProvider`](./opencola-server/core/network/README.md#network-providers) and [`SearchIndex`](./opencola-server/core/search/README.md#search)

With and understanding of the model and core interfaces, you the application can be best unserstood top down starting at the [server](../server/README.md) that hosts the API.