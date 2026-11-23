const environment = document.querySelector(".server-environment");
if (environment) {
    const serviceRows = [...environment.querySelectorAll("[data-status-url]")];
    const refresh = environment.querySelector("[data-status-refresh]");
    const clock = environment.querySelector("[data-status-time]");
    const labels = {up: "정상", warning: "점검 필요", down: "연결 실패", unknown: "확인 불가", checking: "확인 중"};

    function renderStatus(row, {state, detail}) {
        const label = row.querySelector("[data-server-state]");
        label.textContent = labels[state] || labels.unknown;
        label.dataset.state = Object.hasOwn(labels, state) ? state : "unknown";
        row.querySelector("[data-server-detail]").textContent = detail;
    }

    async function check(row) {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 6000);
        try {
            const result = await fetch(row.dataset.statusUrl, {cache: "no-store", signal: controller.signal});
            if (!result.ok) throw new Error("status");
            return await result.json();
        } catch (error) {
            return {state: "unknown", detail: error.name === "AbortError"
                ? "상태 확인 시간이 초과됐습니다." : "웹을 통해 상태를 확인하지 못했습니다."};
        } finally {
            clearTimeout(timeout);
        }
    }

    async function refreshAll() {
        if (refresh.disabled) return;
        refresh.disabled = true;
        clock.textContent = "서버 상태를 확인하는 중입니다.";
        serviceRows.forEach(row => renderStatus(row, {state: "checking", detail: ""}));
        try {
            const results = await Promise.all(serviceRows.map(async row => {
                const result = await check(row);
                renderStatus(row, result);
                return result;
            }));
            if (results[0].state === "unknown") {
                renderStatus(serviceRows[0], {state: "down", detail: results[0].detail});
            }
            clock.textContent = "확인 시각 " + new Date().toLocaleTimeString("ko-KR");
        } finally {
            refresh.disabled = false;
        }
    }
    environment.addEventListener("toggle", () => { if (environment.open) refreshAll(); });
    refresh.addEventListener("click", refreshAll);
    function closePanel() {
        environment.open = false;
        environment.querySelector("summary").focus();
    }
    environment.querySelector("[data-status-close]").addEventListener("click", closePanel);
    environment.addEventListener("keydown", event => {
        if (event.key === "Escape" && environment.open) {
            event.preventDefault();
            closePanel();
        }
    });
}
