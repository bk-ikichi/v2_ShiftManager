// 転記画面：行の追加・クリア、名前を選んだときの申請表示と警告、同じスタッフの二重選択の防止、未保存の確認
(() => {
  const form = document.getElementById('shift-form');
  if (!form) {
    return;
  }

  // その日の申請（userId → {start, end, note}）
  const requests = new Map();
  document.querySelectorAll('#request-data li').forEach((li) => {
    requests.set(li.dataset.userId, { start: li.dataset.start, end: li.dataset.end, note: li.dataset.note || '' });
  });

  let dirty = false;
  form.addEventListener('change', () => { dirty = true; });
  form.addEventListener('submit', () => { dirty = false; });

  // ShiftWarnings と同じ判定（時刻は HH:mm のため文字列のまま比較できる）
  const warningOf = (request, start, end) => {
    if (!request) {
      return '申請がありません';
    }
    if ((start && start < request.start) || (end && end > request.end)) {
      return '申請の時間外です';
    }
    return '';
  };

  // 名前に応じて申請IN・OUT・備考・警告を表示し直す
  const refreshRow = (row) => {
    const userId = row.querySelector('select[data-user]').value;
    const request = requests.get(userId);
    const noteButton = row.querySelector('[data-note-button]');
    const note = row.querySelector('[data-note]');
    const warning = row.querySelector('[data-warning]');
    if (!userId) {
      row.querySelector('[data-request-start]').textContent = '';
      row.querySelector('[data-request-end]').textContent = '';
      noteButton.hidden = true;
      note.hidden = true;
      warning.textContent = '';
      return;
    }
    row.querySelector('[data-request-start]').textContent = request ? request.start : '--:--';
    row.querySelector('[data-request-end]').textContent = request ? request.end : '--:--';
    note.textContent = request ? request.note : '';
    noteButton.hidden = !(request && request.note);
    if (noteButton.hidden) {
      note.hidden = true;
    }
    warning.textContent = warningOf(request, row.querySelector('select[data-in]').value,
      row.querySelector('select[data-out]').value);
  };

  // 他の行で選ばれているスタッフは選べないようにする（サーバー側でも検証する）
  const refreshDuplicates = () => {
    const selects = [...form.querySelectorAll('select[data-user]')];
    const chosen = selects.map((select) => select.value).filter((value) => value);
    selects.forEach((select) => {
      [...select.options].forEach((option) => {
        option.disabled = option.value !== '' && option.value !== select.value && chosen.includes(option.value);
      });
    });
  };

  const setUpRow = (row) => {
    row.querySelectorAll('select').forEach((select) => {
      select.addEventListener('change', () => {
        refreshRow(row);
        refreshDuplicates();
      });
    });
    row.querySelector('[data-note-button]').addEventListener('click', () => {
      const note = row.querySelector('[data-note]');
      note.hidden = !note.hidden;
    });
    // 名前・IN・OUTを空にする（空の行は登録時に削除される）
    row.querySelector('[data-clear]').addEventListener('click', () => {
      row.querySelectorAll('select').forEach((select) => { select.value = ''; });
      dirty = true;
      refreshRow(row);
      refreshDuplicates();
    });
  };

  form.querySelectorAll('[data-row]').forEach(setUpRow);
  refreshDuplicates();

  // 「+ 追加する」：雛形の ROW_INDEX を未使用の添字に置き換えて行を追加する
  let nextIndex = Number(form.dataset.nextIndex);
  form.querySelectorAll('[data-group]').forEach((group) => {
    group.querySelector('[data-add-row]').addEventListener('click', () => {
      const html = group.querySelector('template[data-row-template]').innerHTML
        .replaceAll('ROW_INDEX', String(nextIndex));
      nextIndex += 1;
      const tbody = group.querySelector('[data-rows]');
      tbody.insertAdjacentHTML('beforeend', html);
      const row = tbody.lastElementChild;
      setUpRow(row);
      refreshRow(row);
      refreshDuplicates();
    });
  });

  // 未保存の入力がある状態で別の日へ移動するときは確認する
  document.querySelectorAll('a[data-leave-link]').forEach((link) => {
    link.addEventListener('click', (event) => {
      if (dirty && !window.confirm('保存していない入力があります。移動しますか？')) {
        event.preventDefault();
      }
    });
  });
})();
