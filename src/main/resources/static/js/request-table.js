// 申請一覧：備考の吹き出しの位置合わせ、ドラッグでのスタッフの並び替え（自動保存）、
// 「Excel用にコピー」で時刻だけをタブ区切りでコピーする（1日につきIN・OUTの2列、申請のない日は空欄）

// 備考の吹き出しを i マークの下（入らなければ上）に出し、矢印を i マークに向ける。スクロールしても付いていく
(() => {
  const GAP = 8;
  const MARGIN = 8;

  const place = (button, popover) => {
    const arrow = popover.querySelector('[data-arrow]');
    const anchor = button.getBoundingClientRect();
    const width = popover.offsetWidth;
    const height = popover.offsetHeight;
    // 横は i マークの中心に合わせ、画面からはみ出さないようにする
    const center = anchor.left + anchor.width / 2;
    const left = Math.min(Math.max(center - width / 2, MARGIN), window.innerWidth - width - MARGIN);
    const below = anchor.bottom + GAP + height <= window.innerHeight - MARGIN || anchor.top - GAP - height < MARGIN;
    const top = below ? anchor.bottom + GAP : anchor.top - GAP - height;
    popover.style.inset = 'auto';
    popover.style.left = `${left}px`;
    popover.style.top = `${top}px`;
    // 矢印（45度回した正方形）は吹き出しの枠に半分重ね、外側の2辺だけ線を付ける
    arrow.style.left = `${Math.min(Math.max(center - left - 6, 10), width - 22)}px`;
    arrow.style.top = below ? '-7px' : '';
    arrow.style.bottom = below ? '' : '-7px';
    arrow.style.borderWidth = below ? '1px 0 0 1px' : '0 1px 1px 0';
  };

  // 開いている吹き出しと、その i マーク
  let opened = null;

  document.querySelectorAll('[data-note-button]').forEach((button) => {
    const popover = document.getElementById(button.getAttribute('popovertarget'));
    // 位置が決まるまでは見せない（開いた直後に左上に一瞬出るのを防ぐ）
    popover.addEventListener('beforetoggle', (event) => {
      if (event.newState === 'open') {
        popover.style.visibility = 'hidden';
      }
    });
    popover.addEventListener('toggle', (event) => {
      if (event.newState === 'open') {
        place(button, popover);
        popover.style.visibility = '';
        opened = { button, popover };
      } else if (opened?.popover === popover) {
        opened = null;
      }
    });
  });

  // 表の横スクロールや画面のスクロール・サイズ変更では、i マークに付いていくよう置き直す
  const follow = () => {
    if (opened) {
      place(opened.button, opened.popover);
    }
  };
  window.addEventListener('resize', follow);
  document.addEventListener('scroll', follow, true);
})();

// 名前の左の ≡ を掴んで行をドラッグし、離したら並び順を保存する（HTML5のドラッグ。タッチ操作は非対応）
(() => {
  const table = document.querySelector('table[data-order-url]');
  const tbody = table?.querySelector('tbody');
  const message = document.querySelector('[data-order-message]');
  if (!tbody) {
    return;
  }

  const rows = () => [...tbody.querySelectorAll('tr[data-user-id]')];
  const currentOrder = () => rows().map((row) => row.dataset.userId);

  const show = (text, ok) => {
    message.textContent = text;
    message.classList.toggle('text-green-800', ok);
    message.classList.toggle('text-red-700', !ok);
    if (ok) {
      window.setTimeout(() => { message.textContent = ''; }, 3000);
    }
  };

  const save = async () => {
    const body = new URLSearchParams();
    currentOrder().forEach((id) => body.append('userIds', id));
    try {
      const response = await fetch(table.dataset.orderUrl, {
        method: 'POST',
        headers: { [table.dataset.csrfHeader]: table.dataset.csrfToken },
        body,
      });
      if (!response.ok) {
        throw new Error(await response.text());
      }
      show('並び順を保存しました', true);
    } catch (e) {
      show('並び順を保存できませんでした。ページを開き直してください', false);
    }
  };

  // ドラッグ中の行、ドラッグを始めたときの並び順、表の中で離したか
  let dragging = null;
  let before = null;
  let dropped = false;

  // マスのリンクをドラッグしないよう、≡ を押したときだけ行をドラッグできるようにする
  tbody.addEventListener('mousedown', (event) => {
    const row = event.target.closest('[data-drag-handle]')?.closest('tr');
    if (row) {
      row.draggable = true;
    }
  });
  tbody.addEventListener('mouseup', () => {
    rows().forEach((row) => { row.draggable = false; });
  });

  tbody.addEventListener('dragstart', (event) => {
    const row = event.target.closest?.('tr[data-user-id]');
    if (!row || !row.draggable) {
      return;
    }
    dragging = row;
    before = currentOrder().join(',');
    dropped = false;
    event.dataTransfer.effectAllowed = 'move';
    // Firefox はデータを入れないとドラッグが始まらない
    event.dataTransfer.setData('text/plain', row.dataset.userId);
    row.style.opacity = '0.4';
  });

  // マウスが行の上半分なら前、下半分なら後ろへ入れる
  tbody.addEventListener('dragover', (event) => {
    if (!dragging) {
      return;
    }
    event.preventDefault();
    const target = event.target.closest('tr[data-user-id]');
    if (!target || target === dragging) {
      return;
    }
    const rect = target.getBoundingClientRect();
    const after = event.clientY > rect.top + rect.height / 2;
    tbody.insertBefore(dragging, after ? target.nextSibling : target);
  });

  tbody.addEventListener('drop', (event) => {
    if (dragging) {
      event.preventDefault();
      dropped = true;
    }
  });

  tbody.addEventListener('dragend', () => {
    if (!dragging) {
      return;
    }
    dragging.style.opacity = '';
    dragging.draggable = false;
    dragging = null;
    // Esc や表の外で離したときは、元の並び順に戻す
    if (!dropped) {
      const byId = new Map(rows().map((row) => [row.dataset.userId, row]));
      before.split(',').forEach((id) => tbody.appendChild(byId.get(id)));
      return;
    }
    if (currentOrder().join(',') !== before) {
      save();
    }
  });
})();

(() => {
  const button = document.querySelector('[data-copy-excel]');
  const message = document.querySelector('[data-copy-message]');
  if (!button) {
    return;
  }

  // 表示中の並び順で、1スタッフ＝1行にする
  const buildText = () => [...document.querySelectorAll('tbody tr')]
    .map((row) => [...row.querySelectorAll('[data-cell]')]
      .flatMap((cell) => [cell.dataset.start || '', cell.dataset.end || ''])
      .join('\t'))
    .join('\r\n');

  // http で開いた場合など Clipboard API が使えないときの代わり
  const copyByTextarea = (text) => {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    const copied = document.execCommand('copy');
    textarea.remove();
    if (!copied) {
      throw new Error('copy failed');
    }
  };

  const show = (text) => {
    message.textContent = text;
    window.setTimeout(() => { message.textContent = ''; }, 3000);
  };

  button.addEventListener('click', async () => {
    const text = buildText();
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else {
        copyByTextarea(text);
      }
      show('コピーしました');
    } catch (e) {
      show('コピーできませんでした');
    }
  });
})();
