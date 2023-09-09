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

function waitForServerStart() {
    onImageAvailable("img/pull-tab.png", function () {
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