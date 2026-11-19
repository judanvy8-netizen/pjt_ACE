(() => {
  const dialog = document.getElementById('team-detail');
  if (!dialog || typeof dialog.showModal !== 'function') return;

  const name = document.getElementById('team-detail-name');
  const role = document.getElementById('team-detail-role');
  const description = document.getElementById('team-detail-description');
  document.querySelectorAll('.team-card').forEach((card) => {
    card.querySelector('summary').addEventListener('click', (event) => {
      event.preventDefault();
      name.textContent = card.querySelector('summary strong').textContent;
      role.textContent = card.querySelector('summary span').textContent;
      description.textContent = card.querySelector('p').textContent;
      dialog.showModal();
    });
  });

  dialog.addEventListener('click', (event) => {
    if (event.target === dialog) dialog.close();
  });
})();
