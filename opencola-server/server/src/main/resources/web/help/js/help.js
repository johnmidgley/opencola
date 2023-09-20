const urlFragment = window.location.hash;
const contentSections = document.querySelectorAll(".content section");

contentSections.forEach((section) => {
    if (`#${section.id}` === urlFragment) {
        section.style.display = "block";
    } else {
        section.style.display = "none";
    }
});

// Update the selected section when a navigation link is clicked
const navLinks = document.querySelectorAll("nav a");

navLinks.forEach((link) => {
    link.addEventListener("click", (e) => {
        const targetSectionId = link.getAttribute("href");

        contentSections.forEach((section) => {
            if (`#${section.id}` === targetSectionId) {
                section.style.display = "block";
            } else {
                section.style.display = "none";
            }
        });

        console.info(`Navigating to ${targetSectionId}`);
        window.scrollTo(0, 0);
    });
});
