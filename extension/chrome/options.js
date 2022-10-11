function handleOptionsFormSubmit(event) {
  let serviceUrl = document.getElementById("serviceHost").value
  chrome.storage.sync.set({ serviceUrl });

  let authToken = document.getElementById("authToken").value
  chrome.storage.sync.set({ authToken });
}

function constructOptions() {
    chrome.storage.sync.get("serviceUrl", (data) => {
      let serviceUrl = data.serviceUrl
      let inputServiceUrl = document.getElementById("serviceHost")
      inputServiceUrl.value = serviceUrl;      
    });

    chrome.storage.sync.get("authToken", (data) => {
      let authToken = data.authToken
      let inputAuthToken = document.getElementById("authToken")
      inputAuthToken.value = authToken;      
    });


    let optionsForm = document.getElementById("optionsForm")
    optionsForm.addEventListener("submit", handleOptionsFormSubmit)

}

constructOptions();
