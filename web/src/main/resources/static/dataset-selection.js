const picker = document.querySelector("#photo");
const selection = document.querySelector("#selection");
picker.addEventListener("change", () => {
    const files = Array.from(picker.files);
    picker.setCustomValidity(files.length > 40 ? "사진은 최대 40장까지 등록할 수 있습니다." : "");
    selection.textContent = files.length
        ? `${files.length}장 선택 · ${files.map(file => file.name).join(", ")}`
        : "";
});
