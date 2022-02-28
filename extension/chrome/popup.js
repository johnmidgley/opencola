// Initialize button with user's preferred color
let keepButton = document.getElementById("keep");

chrome.storage.sync.get("color", ({ color }) => {
    // changeColor.style.backgroundColor = color;
});


// When the button is clicked, inject setPageBackgroundColor into current page
keepButton.addEventListener("click", async () => {
    let [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    sendAction(tab, "save", true)
});

function sendAction(tab, action, value){
    // TODO: Check this out https://stackoverflow.com/questions/32194397/why-isnt-chrome-pagecapture-saveasmhtml-working-in-my-google-chrome-extension
    // TODO: Why doesn't console logging work here?

    try {
        // Save to HTML example from https://stackoverflow.com/questions/32194397/why-isnt-chrome-pagecapture-saveasmhtml-working-in-my-google-chrome-extension
        chrome.pageCapture.saveAsMHTML(
            {tabId: tab.id}, function (mhtmlData) {
                // Good resource: https://javascript.info/blob
                let xhr = new XMLHttpRequest(), formData = new FormData();
                formData.append("action", action);
                formData.append("value", value)
                formData.append("mhtml", mhtmlData);
                xhr.open("POST", "http://localhost:5795/action");
                xhr.send(formData);
            }
        )
    } catch (error) {
        alert(error)
        console.log(error)
    }
}


let like = document.getElementById("like");

// When the button is clicked, inject setPageBackgroundColor into current page
like.addEventListener("click", async () => {
    let [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

    alert("like: " + tab.url)
});

let rate = document.getElementById("rate");

// When the button is clicked, inject setPageBackgroundColor into current page
rate.addEventListener("click", async () => {
    let [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

    alert("rate: " + tab.url)
});


