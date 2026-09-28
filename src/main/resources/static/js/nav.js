// ヘッダー：スマホ用メニューの開閉（header の data-open 属性で表示を切り替える）
(() => {
  const header = document.getElementById('site-header');
  if (!header) {
    return;
  }
  const openButton = header.querySelector('[data-nav-open]');

  const setOpen = (open) => {
    header.toggleAttribute('data-open', open);
    openButton.setAttribute('aria-expanded', String(open));
    // 開いている間は後ろの画面をスクロールさせない
    document.body.style.overflow = open ? 'hidden' : '';
  };

  openButton.addEventListener('click', () => setOpen(true));
  header.querySelectorAll('[data-nav-close]').forEach((element) => {
    element.addEventListener('click', () => setOpen(false));
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && header.hasAttribute('data-open')) {
      setOpen(false);
      openButton.focus();
    }
  });
  // 開いたままPC幅に広げた場合は閉じた状態に戻す
  window.matchMedia('(min-width: 64rem)').addEventListener('change', (event) => {
    if (event.matches) {
      setOpen(false);
    }
  });
})();
