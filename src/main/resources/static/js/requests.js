// 申請画面：パターンの自動入力・「この期間は出勤できない」・未保存の確認
(() => {
  const form = document.getElementById('request-form');
  if (!form) {
    return;
  }

  let dirty = false;
  form.addEventListener('input', () => { dirty = true; });
  form.addEventListener('change', () => { dirty = true; });
  form.addEventListener('submit', () => { dirty = false; });

  // パターンを選ぶと同じ行のIN・OUTを入力する（その後の手修正は自由）
  form.querySelectorAll('select[data-pattern]').forEach((select) => {
    select.addEventListener('change', () => {
      const option = select.selectedOptions[0];
      if (!option || !option.dataset.start) {
        return;
      }
      const row = select.closest('[data-row]');
      row.querySelector('select[data-in]').value = option.dataset.start;
      row.querySelector('select[data-out]').value = option.dataset.end;
    });
  });

  // 「この期間は出勤できない」にチェックした期間は入力欄を無効にする（無効な欄は送信されない）
  const applyUnavailable = (checkbox) => {
    const section = checkbox.closest('[data-cycle]');
    section.querySelectorAll('[data-row] select, [data-row] input').forEach((element) => {
      element.disabled = checkbox.checked;
    });
  };
  form.querySelectorAll('input[data-unavailable]').forEach((checkbox) => {
    applyUnavailable(checkbox);
    checkbox.addEventListener('change', () => applyUnavailable(checkbox));
  });

  // 未保存の入力がある状態で月を切り替えるときは確認する
  document.querySelectorAll('a[data-month-link]').forEach((link) => {
    link.addEventListener('click', (event) => {
      if (dirty && !window.confirm('保存していない入力があります。移動しますか？')) {
        event.preventDefault();
      }
    });
  });
})();
