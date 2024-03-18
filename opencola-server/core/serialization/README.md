<img src="../../../img/pull-tab.svg" width="150" />

# serialization

The serialization library contains code for transforming objects into byte arrays and vice versa.

In older versions of OpenCola, custom [```ByteArrayCodec```](./src/main/kotlin/io/opencola/serialization/ByteArrayCodec.kt)s were used to serialize objects, as they were the most compact. Generally all serialization has been moved to [Protocol Buffers](https://protobuf.dev/) (see [```ProtoSerialzable```](./src/main/kotlin/io/opencola/serialization/protobuf/ProtoSerializable.kt)).

While there are build plugins to manage compiling protobuf schema definitions, currently a [custom script](./src/main/proto/compile) is used to compile these schema definitions in order to guarantee that nothing changes unexpectedly. 
