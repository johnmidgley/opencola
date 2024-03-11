<img src="../img/pull-tab.svg" width="150" />

# opencola-server

This directory contains OpenCola server code, which is all written in Kotlin. The components here are:


|Directory|Component|
|---------|---------|
|[`core`](./core/README.md)| Shared libraries for building OpenCola applications. |
|[`relay`](./relay/README.md)| The relay server that allows peers to exchange arbitrary messages.|
|[`server`](./server/README.md)| The server side of the collaboration tool.|
|[`test`](./test/README.md)| Library containing code for testing purposes.|


If you update code and want need to update the version of OpenCola, the `set-version` script updates all appropriate places:

```shell
./set-version X.Y.Z
```

> NOTE: The default of Gradle that IntelliJ used when creating the project is not compatible with Java 17.
Gradle was upgraded with:

```shell
./gradlew wrapper --gradle-version 7.4.1
```