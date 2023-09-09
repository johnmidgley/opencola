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

function addPasswordHasher(form) {
    form.addEventListener('submit', async function (event) {
        event.preventDefault();

        const password = form.elements['password'].value;
        const passwordHash = await sha256(password);

        // Create a hidden form field for the hashed password
        const hiddenForm = document.createElement("form");
        hiddenForm.style.display = "none"; // Hide the form
        hiddenForm.method = "post";
        hiddenForm.action = form.action;

        const hiddenInput = document.createElement("input");
        hiddenInput.type = "hidden";
        hiddenInput.name = "passwordHash";
        hiddenInput.value = passwordHash;

        // Append the hidden input to the hidden form
        hiddenForm.appendChild(hiddenInput);

        // Append the hidden form to the document body
        document.body.appendChild(hiddenForm);

        // Submit the hidden form
        hiddenForm.submit();

        hiddenForm.submit();
    });
}