let serviceUrl = "http://0.0.0.0:5795";

chrome.runtime.onInstalled.addListener(() => {
  chrome.storage.sync.get("serviceUrl", (data) => {
    if(data.serviceUrl == null) {
      console.log("Loaded OpenCola extension")
      chrome.storage.sync.set({ serviceUrl });
      console.log("Default serviceUrl set to " + serviceUrl);
    }
  })
});

chrome.omnibox.onInputEntered.addListener(function(text) {
  chrome.storage.sync.get("serviceUrl", (data) => {
    let serviceUrl = data.serviceUrl

    // Encode user input for special characters , / ? : @ & = + $ #
    const newURL =  serviceUrl + '/#feed?q=' + encodeURIComponent(text);
    chrome.tabs.create({ url: newURL });
  })
});

// chrome.webNavigation.onCompleted.addListener(function(details) {
//     console.log("Nav - oc: " + details.url);
// },
//  // {url: [{hostEquals : '0.0.0.0'}]}
// );
