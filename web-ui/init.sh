# Install Clojure tools (https://clojure.org/guides/getting_started - includes linux)
brew install clojure/tools/clojure

# ClojureScript quick start: https://clojurescript.org/guides/quick-start

# Figwheel
# https://figwheel.org/
# https://figwheel.org/tutorial
# https://figwheel.org/docs/

# Install command line tool
https://clojure.org/guides/getting_started#_installation_on_mac_via_code_brew_code

#
# Leiningen
#

# Create
lein new figwheel-main opencola/search-ui -- --reagent 
cd search-ui

# To run / edit
lein fig:build 

# To test connection
cljs.user=> (js/alert "Am I connected?")


# To create a production build
lein clean
lein fig:min





