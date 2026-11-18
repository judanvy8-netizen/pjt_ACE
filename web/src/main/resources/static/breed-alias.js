(() => {
 const guide = document.getElementById('breed-rules-dialog');
 const detail = document.getElementById('breed-result-dialog');
 document.querySelector('[data-open-rules]')?.addEventListener('click', () => guide?.showModal());
 const form = document.querySelector('[data-alias-search]');
 const output = document.querySelector('[data-alias-results]');
 if (!form || !output) return;
 form.addEventListener('submit', async (event) => {
   event.preventDefault();
   const query = new FormData(form).get('q').trim();
   if (!query) return;
   output.replaceChildren();
   output.textContent = '조회 중…';
   try {
     const response = await fetch('/api/breed-aliases?q=' + encodeURIComponent(query), {headers: {'Accept':'application/json'}});
     if (!response.ok) throw new Error('조회 요청 실패');
     const data = await response.json();
     output.replaceChildren();
     if (!data.length) { output.textContent = '등록된 별칭 규칙이 없습니다.'; return; }
     const summary = document.createElement('p');
     summary.textContent = '검색 결과 ' + data.length + '건 (최대 50건)';
     output.append(summary);
     data.forEach(item => {
       const b = document.createElement('button');
       b.type = 'button'; b.className = 'alias-result';
       b.textContent = item.standard + ' · 상세 보기';
       b.addEventListener('click', () => {
         const dl = detail.querySelector('[data-alias-detail]'); dl.replaceChildren();
         [['표준 품종명', item.standard],['검색 일치 이름', item.matchedName],['등록된 원문/별칭', item.aliases.join(', ')],['판정 정규화 키', item.normalizedKey]].forEach(([label,value]) => {
           const dt = document.createElement('dt'); dt.textContent = label;
           const dd = document.createElement('dd'); dd.textContent = value;
           dl.append(dt,dd);
         });
         detail.showModal();
       });
       output.append(b);
     });
   } catch(e) { output.textContent = '조회에 실패했습니다. 서버 상태를 확인해 주세요.'; }
 });
})();
