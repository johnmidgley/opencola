<img src="../../../img/pull-tab.svg" width="150" />

# (Relay) Server

The relay server allows messages to be passed between clients on the Internet that are separated by firewalls. It also supports message storage so that messages can be exchanged between two peers even if they're not both online at the same time. 

Messages handled by the relay server are fully encrypted. Only the public key of the sender and the public keys of the reciepients are visible to the relay server and the relay server does not log any IP addresses. The relay server **does** log message delivery of messages (including sender and recipient Ids) for diagnostic purposes, which can be turned off.

Documentation for the model, protocol and message storage facilities can be found in [`common`](../common/README.md)

To walk your way through the relay server code, start with [`RelayApplication`](./src/main/kotlin/io/opencola/relay/server/RelayApplication.kt).

## Scaling

The relay server can run in a minimal form as a single instance in a Docker containter with no other dependencies, using ephemeral storage to back a SQLite database and filestore. This is the cheapest way to run things, but limits the number of users that can be serviced simultaneously. The relay server can be scaled in the following ways:

* **Database**: In order to allow more concurrent users and provide more reslient persistence, the database should be switched to something more substantial. By changing the DB connection string, the server can be backed by a Postgres database, which has been tested but not currently used, due to cost. 
* **Filesystem**: Running purely in Docker may limit the amount of storage available to store message bodies and may mean that all data gets effectively ejected when a new server is deployed. To address these issues, you can mount an external file system to `/var/relay`. This has been tested in AWS using EFS and works fine, but is expensive.
* **Server**: The server itself is designed to be horizontally scalled. Whenever a client connects to any instance of the server it registers itself in a [`ConnectionDirectory`](../common/src/main/kotlin/io/opencola/relay/common/connection/ConnectionDirectory.kt). Each relay server automatically forwards messages to the appropriate instance for delivery, so that peers can exchange messages regardless of which instance each peer is connected to. 


## Deployment

Documentation for deploying the relay server can be found in the [`install`](./install/README.md) directory.

## Administration

Administration of the relay is done via the [ocr cli](../cli/README.md).

## Monitoring

The relay server writes an event logs found in `/var/relay/events`. 