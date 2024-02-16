<img src="img/pull-tab.svg" width="150" />

# OpenCola

> Do you want to sell sugar water for the rest of your life, or do you want to come with me and change the world?


This repository contains all the code for OpenCola (the application and toolkit). To understand the overall vision of OpenCola, which puts this code in a larger context please visit our [homepage](https://opencola.io).


If you would like to understand the code bottom up, start in the [```model```](opencola-server/core/model/README.md). If you would like to start top down, start in [```Application.kt```](opencola-server/server/src/main/kotlin/opencola/server/Application.kt)



# Navigating the Code




The important parts of the code are: 

|Directory|Description|
|------|------|
|[```extension```](extension/README.md)| The chrome browser extension (HTML, JS, CSS)|
|[```install```](install/README.md)| Scripts for generating installers (Bash) |
|[```opencola-server```](opencola-server/README.md)| Backend code (oc, relay) (Kotlin) |
|[```web-ui```](web-ui/README.md)| The frontend for the application (Clojurescript)|







