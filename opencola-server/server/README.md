<img src="../../img/pull-tab.svg" width="150" />

# server

This directory contains the server that supports the collaboration tool. It is built using [Ktor](https://ktor.io/]). The main points of interest are:

|File|Description|
|----|-----------|
|[`Application.kt`](./src/main/kotlin/opencola/server/Application.kt)| The entry point for the server.|
|[`Rounting.kt`](./src/main/kotlin/opencola/server/plugins/routing/Routing.kt)|Routing table for API endpoints.|
|[`Feed.kt`](./src/main/kotlin/opencola/server/handlers/Feed.kt)|Where the feed is generated (start in `handleGetFeed()`.|

To start the server for development, you can run the server in your IDE (IntelliJ was used for writing the code) or you can build and run it directly from the command line following the [install](../../install/README.md) instructions.

The UI that interactions with this server is the [`web-ui`](../../web-ui/README.md)
