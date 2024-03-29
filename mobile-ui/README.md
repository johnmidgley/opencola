<img src="../img/pull-tab.svg" width="150" alt="OpenCola"/>

# mobile-ui

The directory contains a Flutter UI for mobile devices. It is a work in progress and requires the OpenCola server (used by the web-ui) to be running to host the backend API. 

# Setup

* Install XCode from the AppStore
* `brew install flutter` (got Flutter 3.19.4)
* `flutte pub get`
* In Code, install "Flutter" extension
* `flutter doctor` may indicate missing XCode modules:
* `sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer`
* `sudo xcodebuild -runFirstLaunch`
* `sudo gem install cocoapods`. May get errors, but follow directions a few times, and should work
* Install Android Studio (full install to avoid hassles)


Check with `flutter doctor`
# Run

Shift + CMD + D: May need to create run configuration - default should work
