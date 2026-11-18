const historyForm = document.querySelector("#failed-history-form");
const reviewDelete = document.querySelector("[data-delete-review]");
if (historyForm && reviewDelete) {
    const choices = [...historyForm.querySelectorAll("input[name=runIds]")];
    const selectAll = historyForm.querySelector("[data-select-failed]");
    const count = document.querySelector("[data-selected-count]");
    const confirmation = historyForm.querySelector("[data-delete-confirmation]");
    const question = confirmation.querySelector("[data-delete-question]");
    const targets = confirmation.querySelector("[data-delete-targets]");
    const submit = confirmation.querySelector("[data-delete-submit]");
    const selected = () => choices.filter(choice => choice.checked);

    function updateSelection() {
        const checked = selected();
        count.textContent = checked.length + "건 선택";
        reviewDelete.disabled = checked.length === 0;
        selectAll.checked = checked.length === choices.length;
        selectAll.indeterminate = checked.length > 0 && !selectAll.checked;
        confirmation.hidden = true;
        choices.forEach(choice => choice.closest("tr").classList.toggle("row-selected", choice.checked));
    }

    selectAll.disabled = false;
    selectAll.addEventListener("change", () => {
        choices.forEach(choice => { choice.checked = selectAll.checked; });
        updateSelection();
    });
    choices.forEach(choice => choice.addEventListener("change", updateSelection));
    reviewDelete.addEventListener("click", () => {
        const checked = selected();
        if (checked.length === 0) return;
        question.textContent = "선택한 실패 기록 " + checked.length + "건을 삭제할까요?";
        targets.replaceChildren(...checked.map(choice => {
            const item = document.createElement("li");
            item.textContent = choice.dataset.photo + " · " + choice.dataset.engine;
            return item;
        }));
        submit.textContent = checked.length + "건 삭제";
        confirmation.hidden = false;
        question.focus();
    });
    confirmation.querySelector("[data-delete-cancel]").addEventListener("click", () => {
        confirmation.hidden = true;
        reviewDelete.focus();
    });
    historyForm.addEventListener("submit", event => {
        if (confirmation.hidden || selected().length === 0) event.preventDefault();
    });
    updateSelection();
}
