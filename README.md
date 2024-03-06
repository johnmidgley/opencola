<img src="img/pull-tab.svg" width="150" />


# OpenCola


This repository contains the code for the OpenCola application and toolkit. To understand the overall vision of OpenCola, which puts this code in a larger context please visit our [homepage](https://opencola.io).

The application is a collaboration tool that looks a lot like a social media application, but puts you in control of your personal data as well as the flow of information around you. This means that, unless you decide otherwise, there is no 3rd party ingerference (ads, algorithms, trolls, scammers, etc.). You also control where your data lives, which is by default on your local device, shared with only peers of your choice. 

The toolkit provides is a flexible data model, a set of simple interfaces (and supporting libraries, as well as a relay server that allows peer nodes to communicate in a trustworthy way. The toolkit is very general, and could be used to build many applications across arbitrary domains. 

# Navigating the Code

If you would like to understand the code bottom up, start in the [```model```](opencola-server/core/model/README.md). If you would like to start top down, start in [```Application.kt```](opencola-server/server/src/main/kotlin/opencola/server/Application.kt)

The important parts of the code are: 

|Directory|Description|
|------|------|
|[```opencola-server```](opencola-server/README.md)| Backend code (oc, relay) (Kotlin) |
|[```web-ui```](web-ui/README.md)| The frontend for the application (Clojurescript)|
|[```extension```](extension/README.md)| The chrome browser extension (HTML, JS, CSS)|
|[```install```](install/README.md)| Scripts for generating installers (Bash) |







