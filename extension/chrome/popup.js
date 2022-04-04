let baseServiceUrl = "http://0.0.0.0:5795"
let statusImg = document.getElementById("status")


chrome.storage.sync.get("serviceUrl", (data) => {
  let serviceUrl = data.serviceUrl
  baseServiceUrl = serviceUrl
})


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


async function sendAction(tab, action, value){
    // TODO: Check this out https://stackoverflow.com/questions/32194397/why-isnt-chrome-pagecapture-saveasmhtml-working-in-my-google-chrome-extension
    // TODO: Why doesn't console logging work here?

    setStatus("yellow")

    try {
        // Save to HTML example from https://stackoverflow.com/questions/32194397/why-isnt-chrome-pagecapture-saveasmhtml-working-in-my-google-chrome-extension
        chrome.pageCapture.saveAsMHTML(
            {tabId: tab.id}, function (mhtmlData) {
                // Good resource: https://javascript.info/blob
                let xhr = new XMLHttpRequest(), formData = new FormData()
                formData.append("action", action)
                formData.append("value", value)
                formData.append("mhtml", mhtmlData)
                xhr.open("POST", baseServiceUrl + "/action")
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


document.getElementById("save").addEventListener("click", async () => {
    // TODO: For some reason, can't move this call inside send action.
  let [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
  await sendAction(tab, "save", true)
});


document.getElementById("like").addEventListener("click", async () => {
  let [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
  await sendAction(tab, "like", true)
});


document.getElementById("trust").addEventListener("click", async () => {
  let [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
  await sendAction(tab, "trust", 1.0)
});

document.getElementById("search").addEventListener("click", async () => {
  window.open(baseServiceUrl, "_blank")
});



