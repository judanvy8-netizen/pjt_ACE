(() => {
  const button = document.querySelector('[data-theme-toggle]');
  if (!button) return;
  const refresh = () => {
    const dark = document.documentElement.dataset.theme === 'dark';
    button.textContent = dark ? '☀' : '☾';
    button.setAttribute('aria-label', dark ? '라이트 테마 켜기' : '다크 테마 켜기');
    button.setAttribute('aria-pressed', String(dark));
    button.title = dark ? '라이트 테마로 전환' : '다크 테마로 전환';
  };
  button.addEventListener('click', () => {
    const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    try { localStorage.setItem('ace-theme', next); } catch(e) { /* private mode */ }
    refresh();
  });
  refresh();
})();
