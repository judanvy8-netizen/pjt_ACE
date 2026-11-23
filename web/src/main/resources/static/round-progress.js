const endpoint = document.querySelector("[data-progress]")?.dataset.progress;
const progress = document.querySelector("#round-progress");

if (endpoint) {
    async function poll() {
        try {
            const response = await fetch(endpoint, {cache: "no-store"});
            if (!response.ok) throw new Error("진행 조회 오류");
            const data = await response.json();
            const text = `전체 ${data.total}건 · 성공 ${data.success} · 실패 ${data.failed} · 삭제 ${data.dismissed}`;
            progress.textContent = text;
            if (!data.running) {
                window.location.reload();
                return;
            }
        } catch {
            progress.textContent = "진행 상태 연결을 다시 확인하는 중입니다.";
        }
        window.setTimeout(poll, 2500);
    }
    window.setTimeout(poll, 2500);
}
