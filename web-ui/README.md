<img src="../img/pull-tab.svg" width="150" alt="OpenCola"/>

# web-ui

This directory contains the web interface for the collaboration tool. It is written in [ClojureScript](https://clojurescript.org/) using [Reagent](https://reagent-project.github.io/) with [Leiningen](https://leiningen.org/) and [Figwheel](https://figwheel.org/) tooling. 

## Setup

Install [Clojure](https://clojure.org/guides/getting_started), which on MacOS is:

```
brew install clojure/tools/clojure
```

> If you're unfamiliar with Clojure and want to learn the basics, follow the [quick start](https://clojurescript.org/guides/quick-start).

Install [Leiningen](https://leiningen.org/), which automates Clojure builds"

```
brew install leiningen
```

## Developing

[Figwheel](https://figwheel.org/) provides a dynamic way of building Clojurescript applications, where the UI is updated whenever the source code is saved. 

Before starting the `web-ui`, make sure that the API [server](../opencola-server/server/README.md) is running. Since the figwheel server and the backend server are running on different ports, you will need to disable authentication so that the login flow doesn't try to breakout of fighweel. You can do this by editing your `opencola-server.yaml` file or setting the following environment variables:

```
security.login.password=test # Change this if you have already set a password
security.login.authenticationRequired=false
```


Once the server is running, in another command line window, start Figwheel for development with

```
lein fig:build 
```

This may take a bit to get everything running, but when done, it will start a repl and a web session in your defaul browser. 

You can test the repl / browser connection by typing:
```
cljs.user=> (js/alert "Am I connected?")
```

This should cause a pop-up to appear in the browser.

Figwheel is generally very good at updatin any code in the browser when the source is saved. Occassionally, things can get missed. If you ever don't see an expected change in the UI, try doing a forced refresh in the browser (holding SHIFT and clicking the refresh button works in most browsers).

## Deploying

Any edits you make to source code in the `web-ui` directory don't affect the server until they are deployed (copied into the server resources). To do this, you'll want to create a clean, minimized build and the deloy it, which is done with the deploy script:

```
./deploy
```







