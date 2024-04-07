<img src="../img/pull-tab.svg" width="150" alt="OpenCola"/>

# Install

This directory contains scripts for building and packaging the OpenCola application
    
> TODO: move to ```opencola-server/server``` directory - currently here for legacy reasons.

To install OpenCola, simply run the ```./install``` script. This runs the gradle build script and copies the resulting build into the opencola directory.

To run the server directly:

```bash
cd opencola/server/bin
./opencola-server
```

If you'd like to run inside Docker (without packaging), there are ```start``` and ```stop``` scripts in each OS specific directory. These scripts are really only needed to determine IP addresses to be used in https certificates for the server. You can start without these scripts if you are fine using ```localhost``` or ```127.0.0.1``` when access the server.

# Packaging 

Once the server is installed, it can be packaged for various platforms.

## MacOS

Change to the mac directory:

```sh
cd mac
```

You will need an application signing key from Apple. Once you have one, update ```--mac-signing-key-user-name``` in the package script and then run:

```sh
./package
```

This creates an installer ```.dmg```.

## Windows

Change to the Windows directory:

```sh
cd windows
```

For some reason, it's hard to run the oc-version script and pass the value to the windows packaging script, so for now, you need to specify the version manually. You can find the version in the [build.gradle.kts](../opencola-server/build.gradle.kts) or by running [oc-version.ps1](../bin/oc-version.ps1).

```sh
package VERSION
```

This creates a Windows installer.

## Linux

Change to the Linux directory:

```sh
cd linux
```

Run the package script:

```sh
./package
```

This creates a tar archive.

# Docker

> TODO: Publish to Docker Hub

If you'd like to create a self contained Docker distribution, just run the package script from the install directory:

```sh
./package
```

This creates a ```.zip``` file with everything needed to run OpenCola in Docker.
