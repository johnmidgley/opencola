chrome.storage.sync.get("color", ({ color }) => {
    // changeColor.style.backgroundColor = color;
});

let statusImg = document.getElementById("status")

function setStatus(status){
    switch(status){
        case "red":
        case "yellow":
        case "green":
            statusImg.src ="images/" + status + "-light-icon.svg";
            return true
        default:
            console.log("Invalid status: " + status)
            return false
    }
}

let keepButton = document.getElementById("keep");

// When the button is clicked, inject setPageBackgroundColor into current page
keepButton.addEventListener("click", async () => {
    // TODO: For some reason, can't move this call inside send action.
    let [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

    await sendAction(tab, "save", true)
});



async function sendAction(tab, action, value){
    // TODO: Check this out https://stackoverflow.com/questions/32194397/why-isnt-chrome-pagecapture-saveasmhtml-working-in-my-google-chrome-extension
    // TODO: Why doesn't console logging work here?

    setStatus("yellow")

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
                xhr.onreadystatechange = function () {
                    // In local files, status is 0 upon success in Mozilla Firefox
                    if(xhr.readyState === XMLHttpRequest.DONE) {
                        let status = xhr.status;
                        if (status >= 200 && status < 400) {
                            // The request has been completed successfully
                            setStatus("green")
                        } else {
                            setStatus("red")
                            // Oh no! There has been an error with the request!
                        }
                    }
                };
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

    await sendAction(tab, "like", true)
});

let rate = document.getElementById("rate");

// When the button is clicked, inject setPageBackgroundColor into current page
rate.addEventListener("click", async () => {
    let [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

    await sendAction(tab, "rate", 1.0)
});


