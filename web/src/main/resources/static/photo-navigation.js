const photoSelect = document.querySelector("#photo-select");
if (photoSelect) {
    photoSelect.addEventListener("change", () => {
        if (photoSelect.value) window.location.assign(photoSelect.value);
    });
}
