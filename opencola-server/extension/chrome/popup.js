// Initialize button with user's preferred color
let changeColor = document.getElementById("changeColor");

chrome.storage.sync.get("color", ({ color }) => {
    changeColor.style.backgroundColor = color;
});


// When the button is clicked, inject setPageBackgroundColor into current page
changeColor.addEventListener("click", async () => {
    let [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

    // TODO: Check this out https://stackoverflow.com/questions/32194397/why-isnt-chrome-pagecapture-saveasmhtml-working-in-my-google-chrome-extension
    // TODO: Why doesn't console logging work here?

    try {
        // Save to HTML example from https://stackoverflow.com/questions/32194397/why-isnt-chrome-pagecapture-saveasmhtml-working-in-my-google-chrome-extension
        chrome.pageCapture.saveAsMHTML(
            {tabId: tab.id}, function (mhtmlData) {
                // Good resrouce: https://javascript.info/blob
                let xhr = new XMLHttpRequest(), formData = new FormData();
                formData.append("action", "action");
                formData.append("mhtml", mhtmlData);
                xhr.open("POST", "http://localhost:5795/action");
                // xhr.setRequestHeader('Authorization', 'Token token=<redacted>');
                xhr.send(formData);

                // Alternate version that sends JSON object. Opted not to do this because
                // payload was significatnly bigger
                //
                // var reader = new FileReader();
                // reader.onloadend = function(evt) {
                //     // file is loaded
                //     formData.append("action", "click");
                //     formData.append("mhtml", evt.target.result);
                //     xhr.open("POST", "http://localhost:5795/action", false);
                //     xhr.send(JSON.stringify({"action": "click", "mhtml": evt.target.result}));
                // };
                // reader.readAsDataURL(mhtmlData);
            }
        )
    } catch (error) {
        alert(error)
        console.log(error)
    }
    // alert(tab.url)

    // chrome.scripting.executeScript({
    //     target: { tabId: tab.id },
    //     function: setPageBackgroundColor,
    // });
});


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

// The body of this function will be executed as a content script inside the
// current page
function setPageBackgroundColor() {
    chrome.storage.sync.get("color", ({ color }) => {
        document.body.style.backgroundColor = color;
    });
}
