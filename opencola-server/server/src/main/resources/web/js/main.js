function onImageAvailable( src, onSuccess ) {
    console.log("Trying")

    let img = new Image();
    img.onload = function () {
        onSuccess();
    };

    img.onerror = function () {
        setTimeout(onImageAvailable, 1000, src, onSuccess);
    };

    img.src = src;
}

function onPageAvailable( url, onSuccess ) {
    let xhr = new XMLHttpRequest();
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4) {
            if (xhr.status === 200)
                onSuccess();
            else
                setTimeout(onPageAvailable, 1000, url, onSuccess);
        }
    };

    xhr.open("GET", url, true);
    xhr.send();
}

function waitForServerStart() {
    onPageAvailable("index.html", function () {
        window.location = '/';
    });
}

async function sha256(text) {
    // Create a TextEncoder to convert the password to bytes
    const encoder = new TextEncoder();
    const data = encoder.encode(text);

    // Use the SubtleCrypto API to hash the password with SHA-256
    const hashBuffer = await crypto.subtle.digest("SHA-256", data);

    // Convert the hash to a hexadecimal string
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(byte => byte.toString(16).padStart(2, '0')).join('');
}

function onSubmitHashFormFields(form, fields) {
    form.addEventListener('submit', async function (event) {
        event.preventDefault();

        // Clone node to visibly hide the fact that fields are being altered
        const hiddenForm = form.cloneNode(true);
        hiddenForm.hidden = true;

        // Hash the specified fields
        for(const field of fields) {
            const fieldElement = hiddenForm.elements[field];
            fieldElement.value = await sha256(fieldElement.value);
        }

        // Submit the altered form
        document.body.appendChild(hiddenForm);
        hiddenForm.submit();
    });
}