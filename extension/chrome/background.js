let serviceUrl = "http://0.0.0.0:5795";

chrome.runtime.onInstalled.addListener(() => {
    console.log("Loaded openCOLA extension")
    chrome.storage.sync.set({ serviceUrl });
    console.log("Default serviceUrl set to " + serviceUrl);
});

chrome.omnibox.onInputEntered.addListener(function(text) {
    // Encode user input for special characters , / ? : @ & = + $ #
    const newURL = 'https://www.google.com/search?q=' + encodeURIComponent(text);
    chrome.tabs.create({ url: newURL });
});
