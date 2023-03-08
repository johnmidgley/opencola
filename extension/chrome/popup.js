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

function setPersona(id) {
    chrome.storage.sync.set({"persona-id": id}, () => {
        console.log("Persona set to: " + id)
    })
}

function getPersona(onSuccess, defaultPersonaId = null) {
    chrome.storage.sync.get("persona-id", (data) => {
        let personaId = data["persona-id"]

        onSuccess(personaId)
    })
}

function setPersonaDefault(id) {
    // getPersonaId and if not set, set it to the default
    getPersona((personaId) => {
        if (personaId == null || personaId == undefined || personaId == "")
            setPersona(id)
    })
}

function removePersona() {
    chrome.storage.sync.remove("persona-id")
}

function getPersonas() {
  var xhttp = new XMLHttpRequest();
  xhttp.onreadystatechange = function() {
    if (this.readyState == 4) {
        if(this.status == 200) {
            let personas = JSON.parse(this.responseText).items
            let select = document.getElementById("personas")
            select.onchange = function() { setPersona(this.value) }

            if(personas.length > 0)
                setPersonaDefault(personas[0].id)

            for(let i = 0; i < personas.length; i++) {
                let persona = personas[i]
                let option = document.createElement("option")
                option.value = persona.id
                option.text = persona.name
                select.add(option)
            }

            getPersona((personaId) => {
                if(personaId != undefined)
                select.value = personaId
            })
        } else {
          alert("Unable to authenticate extension.\n A login page will be opened.")
          window.close()
          window.open(baseServiceUrl + '/login', "_blank")
        }
    }
  };
  xhttp.open("GET",  baseServiceUrl + '/personas', true);
  xhttp.send();
}

chrome.storage.sync.get("serviceUrl", (data) => {
  let serviceUrl = data.serviceUrl
  baseServiceUrl = serviceUrl
  
  let xhttp = new XMLHttpRequest()
  xhttp.onreadystatechange = function() {
    if (this.readyState == 4) {
        if(this.status == 0) {
          alert("Can't connect to server:\n" + baseServiceUrl + "\nPlease start it and try again.")
          window.close()
        } else {
            getPersonas()
          setStatus("green")
        }
    }
  };

  xhttp.open("GET", baseServiceUrl)
  xhttp.send();
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

                getPersona((personaId) => {
                    xhr.open("POST", baseServiceUrl + "/action?personaId=" + personaId)
                    xhr.setRequestHeader("authToken", authToken)
                    xhr.onreadystatechange = function () {
                        // In local files, status is 0 upon success in Mozilla Firefox
                        if (xhr.readyState === XMLHttpRequest.DONE) {
                            let status = xhr.status;
                            if (status >= 200 && status < 400) {
                                // The request has been completed successfully
                                setStatus("green")
                            } else {
                                setStatus("red")

                                if (status == 401)
                                    login()
                            }
                        }
                    };
                    xhr.send(formData);
                })
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



