# Building Model.java

How to use Cap'n Proto in OpenCola can be found in [../../serialization/capnproto/README.md](../../serialization/capnproto/capnproto.md)

To compile the schema for java, run the following commands:
```
CAPNPROTO_JAVA=/path/to/capnproto-java/project
cp $CAPNPROTO_JAVA/compiler/src/main/schema/capnp/java.capnp .
capnp compile -o$CAPNPROTO_JAVA/capnpc-java model.capnp
rm java.capnp
```