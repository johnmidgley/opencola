let color = '#3aa757';

chrome.runtime.onInstalled.addListener(() => {
    console.log("Loaded openCOLA extension")
    chrome.storage.sync.set({ color });
    console.log('Default background color set to %cgreen', `color: ${color}`);
});

chrome.omnibox.onInputEntered.addListener(function(text) {
    // Encode user input for special characters , / ? : @ & = + $ #
    console.log("Hi")
    const newURL = 'https://www.google.com/search?q=' + encodeURIComponent(text);
    chrome.tabs.create({ url: newURL });
});