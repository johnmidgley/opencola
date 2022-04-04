function handleOptionsFormSubmit(event){
  let serviceUrl = document.getElementById("serviceHost").value
  chrome.storage.sync.set({ serviceUrl });
}

function constructOptions() {
    chrome.storage.sync.get("serviceUrl", (data) => {
    let serviceUrl = data.serviceUrl
    let inputServiceUrl = document.getElementById("serviceHost")
    inputServiceUrl.value = serviceUrl;

    let optionsForm = document.getElementById("optionsForm")
    optionsForm.addEventListener("submit", handleOptionsFormSubmit)

    });
}

constructOptions();
