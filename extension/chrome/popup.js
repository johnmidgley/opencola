let baseServiceUrl = "https://localhost:5796"
let statusImg = document.getElementById("status")
let authToken = null

function parseCookie(str) {
  return str
    .split(';')
    .map(v => v.split('='))
    .reduce((acc, v) => {
      acc[decodeURIComponent(v[0].trim())] = decodeURIComponent(v[1].trim());
      return acc;
    }, {});
}

function login() {
  var xhttp = new XMLHttpRequest();
  xhttp.onreadystatechange = function() {
    if (this.readyState == 4) {
        if(this.status != 200) {
          alert("Unable to authenticate extension. Is your OpenCola server running? A login page will be opened to check..")
          window.close()
          window.open(baseServiceUrl + '/login', "_blank")
        }
    }
  };
  xhttp.open("GET",  baseServiceUrl + '/login', true);
  xhttp.send();
}

function getAuthToken() {
  chrome.cookies.get(
    { url: baseServiceUrl, name: 'user_session' },
    function (cookie) {
      if (cookie) {
        authToken = parseCookie(decodeURIComponent(cookie.value))["authToken"].replace(/^(#s)/, '')
        console.log(authToken)
      }
      else {
        login()
      }
    });
}


chrome.storage.sync.get("serviceUrl", (data) => {
  let serviceUrl = data.serviceUrl
  baseServiceUrl = serviceUrl
  getAuthToken()
})


function setStatus(status) {
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


async function sendAction(tab, action, value) {
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
                xhr.setRequestHeader("authToken", authToken)
                xhr.onreadystatechange = function () {
                    // In local files, status is 0 upon success in Mozilla Firefox
                    if(xhr.readyState === XMLHttpRequest.DONE) {
                        let status = xhr.status;
                        if (status >= 200 && status < 400) {
                            // The request has been completed successfully
                            setStatus("green")
                        } else {
                            setStatus("red")

                            if(status == 401)
                              login()
                        }
                    } else {
                      console.log("Status: " + xhr.status)
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



