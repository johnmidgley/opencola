# Cap'n Proto
OpenCola use [Cap’n Proto](https://capnproto.org/) for serialization. 

The shared java.capnp file is stored in this directory, and refered to by modules for compiling Java schemas.

In order to compile schemas, the ```capnp``` tool needs to be installed as well as ```capnproto-java```.

# Installing ```capnp```

Installation instruction can be found [here](https://capnproto.org/install.html#installation-unix)

The method used here, for Linux, was to install from git.

> Note:
> * ```apt-get install capnproto``` didn't seem to place required headerfiles properly

In order to install from git, autoconf, automake, and libtool need to be enabled. Libtool wasn't installed by default, which was corrected by

```
sudo apt-get libtool
```

Then Cap'n Proto was installed:

```
git clone https://github.com/capnproto/capnproto.git
cd capnproto/c++
autoreconf -i
./configure
make -j6 check
sudo make install
```

# Installing ```capnproto-java```

[```capnproto-java``` docs](https://dwrensha.github.io/capnproto-java/index.html)

```
git clone https://github.com/capnproto/capnproto-java
cd capnproto-java
make
```

# Compiling a schema
Create a capnproto schema with java specifc annotations. The head of your schema file should look something like this:

```
@0xb38c382d763e68ad; # DO NOT USE this exact value
                     # This should be unique per schema.

using Java = import "java.capnp";    
$Java.package("io.opencola.model");  
$Java.outerClassname("Transaction"); 
```

To compile the schema, run the following. 

> NOTE: In order to avoid duplicating java.capnp, we simply copy it, compile, and then remove it. This could also be done by modifying the import, but since the import does not allow relative paths, and the compiler is outside the project, copying is probably the best choice. 

```
CAPNPROTO_JAVA=/path/to/capnproto-java/project
cp $CAPNPROTO_JAVA/compiler/src/main/schema/capnp/java.capnp .
capnp compile -o$CAPNPROTO_JAVA/capnpc-java model.capnp
rm java.capnp
```

