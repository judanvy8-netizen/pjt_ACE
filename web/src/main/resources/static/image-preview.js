const dialog = document.querySelector("#image-preview");
if (dialog && typeof dialog.showModal === "function") {
    let trigger;
    const image = dialog.querySelector("img");
    document.querySelectorAll("a[data-preview]").forEach(link => {
        link.addEventListener("click", event => {
            if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            event.preventDefault();
            trigger = link;
            const filename = link.closest("[data-filename]").dataset.filename;
            const title = `${filename} · ${link.dataset.preview}`;
            dialog.querySelector("h2").textContent = title;
            image.alt = title;
            image.src = link.href;
            dialog.showModal();
        });
    });
    dialog.addEventListener("close", () => {
        image.removeAttribute("src");
        trigger?.focus({preventScroll: true});
    });
}
