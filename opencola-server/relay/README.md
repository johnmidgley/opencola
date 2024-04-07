<img src="../../img/pull-tab.svg" width="150" />

# Relay

Devices on the Internet often cannot communicate with each other directly, as they are on different networks protected by firewalls. The Relay is a key piece of the OpenCola toolkit that allows peers to communicate through a known server. In addition to providing a communication path between devices, it also supports storing messages for offline recipients that can be retrieved later, so that devices don't need to be online at the same time to exchange messages.

You can run the OpenCola application without a relay server, but this requires that your peers be on the same network or VPN, in which case peers can communicate directly of ```http```. Even in such situations, though, it's best to use a 'local' relay server, since it can store and forward messages. 

The relay server has the the following components:

|Component|Description|
|---------|-----------|
|[`common`](./common/README.md)|Code common to other components. Start here to understand the protocol and data model.|
|[`server`](./server/README.md)|The relay server code. Instructions for deployment can be found here.|
|[`client`](./client/README.md)|The client library used by applications to send messages via the relay server.|
|[`cli`](./cli/README.md)|The OpenCola relay command line interface for administering the server.|
