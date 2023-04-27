# Install Protobuf

Update / install build dependencies:
```
sudo apt-get update
sudo apt-get install autoconf automake libtool curl make g++ unzip
```
Prepare the source:
```
curl -LO https://github.com/protocolbuffers/protobuf/releases/download/v3.17.3/protobuf-all-3.17.3.tar.gz
tar xvzf protobuf-all-3.17.3.tar.gz
cd protobuf-3.17.3/
```
Compile and install:
```
./configure
make
make check
sudo make install
sudo ldconfig # refresh shared library cache
```

# Compile the Schema
> NOTE: The kotlin DSL doesn't seem to work out of the box. Looks like it's an old issue that should have been closed.
```
protoc  --java_out=../../../../  model.proto
```
OR
```
./compile
```

# Add dependencies to build.gradle.kts
```
implementation("com.google.protobuf:protobuf-java:3.22.2")
```
