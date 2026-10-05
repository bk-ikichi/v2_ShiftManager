// 説明書（ヘルプ画面）の画像とPDFを作り直す。npm run manual で実行する
// 前提：Docker Desktop で compose の db が起動していること、Edge がインストールされていること
// 流れ：説明書専用DBを作り直す → アプリを8081で起動 → デモデータを入れる → 撮影 → PDF出力 → アプリを停止
import { execFileSync, execSync, spawn } from 'node:child_process';
import { mkdirSync, openSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { chromium } from 'playwright-core';

const PORT = 8081;
const BASE = `http://localhost:${PORT}`;
const DB = 'shiftmanager_manual';
// デモデータ共通のパスワード（説明書専用DBでのみ使う）
const PASSWORD = 'manual-pass1';
// 画像とPDFの保存先。target 側にも書くのは、起動中のアプリが新しい画像を配信できるようにするため
const STATIC_DIRS = ['src/main/resources/static', 'target/classes/static'];
const PHONE = { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 };
const PC = { viewport: { width: 1280, height: 800 }, deviceScaleFactor: 1 };
const isWindows = process.platform === 'win32';

// 日付（日本時間、YYYY-MM-DD の文字列で扱う）
const TODAY = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Tokyo' }).format(new Date());

function addDays(text, days) {
  const date = new Date(`${text}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** 翌月（YYYY-MM） */
function nextMonth(text) {
  const [year, month] = text.split('-').map(Number);
  return new Date(Date.UTC(year, month, 1)).toISOString().slice(0, 7);
}

/** 指定日より後で最初のサイクル開始日（1日・11日・21日）。demo-data.sql の next_cycle と同じ */
function nextCycleStart(text) {
  const [year, month, day] = text.split('-').map(Number);
  if (day < 11) return `${text.slice(0, 8)}11`;
  if (day < 21) return `${text.slice(0, 8)}21`;
  return `${nextMonth(text)}-01`;
}

/** compose の db コンテナで psql を実行する */
function psql(database, args, input) {
  execFileSync('docker', ['compose', 'exec', '-T', 'db', 'psql', '-U', 'shiftmanager', '-d', database,
    '-v', 'ON_ERROR_STOP=1', ...args], { input, stdio: ['pipe', 'ignore', 'inherit'] });
}

function checkDatabase() {
  try {
    execFileSync('docker', ['compose', 'exec', '-T', 'db', 'pg_isready', '-U', 'shiftmanager'], { stdio: 'ignore' });
  } catch {
    throw new Error('compose の DB が起動していません。docker compose up -d db を実行してください');
  }
}

async function isUp() {
  try {
    return (await fetch(`${BASE}/login`)).ok;
  } catch {
    return false;
  }
}

function startApp() {
  const log = openSync('target/manual-app.log', 'w');
  const env = {
    ...process.env,
    DATABASE_URL: `jdbc:postgresql://localhost:5433/${DB}`,
    DATABASE_USERNAME: 'shiftmanager',
    DATABASE_PASSWORD: 'shiftmanager',
    STAFF_INITIAL_PASSWORD: 'manual-initial1',
  };
  // 一時ディレクトリを普段の起動と分ける。Windows では管理者権限の有無で既存の一時ディレクトリの所有者チェックに失敗するため
  const tmp = join(process.cwd(), 'target', 'manual-tmp');
  mkdirSync(tmp, { recursive: true });
  const args = ['spring-boot:run', `-Dspring-boot.run.arguments=--server.port=${PORT}`,
    `-Dspring-boot.run.jvmArguments=-Djava.io.tmpdir=${tmp}`];
  // Windows の .cmd は cmd.exe 経由でないと起動できない。カレントディレクトリを探さない環境があるため絶対パスで指定する
  // それ以外はプロセスグループごと止められるよう detached にする
  return isWindows
    ? spawn('cmd.exe', ['/c', join(process.cwd(), 'mvnw.cmd'), ...args], { env, stdio: ['ignore', log, log] })
    : spawn('./mvnw', args, { env, detached: true, stdio: ['ignore', log, log] });
}

async function waitForApp(app) {
  const limit = Date.now() + 180_000;
  while (Date.now() < limit) {
    if (app.exitCode !== null) throw new Error('アプリが起動中に終了しました。target/manual-app.log を確認してください');
    if (await isUp()) return;
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error('アプリが3分以内に起動しませんでした。target/manual-app.log を確認してください');
}

function stopApp(app) {
  if (app.exitCode !== null) return;
  try {
    if (isWindows) {
      // mvnw から起動した java も含めて止める
      execFileSync('taskkill', ['/pid', String(app.pid), '/T', '/F'], { stdio: 'ignore' });
    } else {
      process.kill(-app.pid, 'SIGTERM');
    }
  } catch {
    // 既に終了している
  }
}

function save(relativePath, data) {
  for (const dir of STATIC_DIRS) {
    const path = join(dir, relativePath);
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, data);
  }
  console.log(`保存しました：${relativePath}`);
}

async function shot(page, relativePath, url, { fullPage = false } = {}) {
  if (url) await page.goto(BASE + url, { waitUntil: 'networkidle' });
  if (!fullPage) {
    save(relativePath, await page.screenshot());
    return;
  }
  // screenshot の fullPage では画面下に固定した要素（転記の「登録する」）が撮影前の位置に写るため、
  // ビューポートをページの高さまで広げてから撮り、元に戻す
  const viewport = page.viewportSize();
  const height = await page.evaluate(() => document.documentElement.scrollHeight);
  await page.setViewportSize({ width: viewport.width, height: Math.max(height, viewport.height) });
  save(relativePath, await page.screenshot());
  await page.setViewportSize(viewport);
}

async function login(browser, loginId, device) {
  const context = await browser.newContext(device);
  const page = await context.newPage();
  await page.goto(`${BASE}/login`);
  await page.fill('input[name="loginId"]', loginId);
  await page.fill('input[name="password"]', PASSWORD);
  await page.click('button:has-text("ログイン")');
  await page.waitForLoadState('networkidle');
  return { context, page };
}

async function staffShots(browser) {
  const guest = await browser.newContext(PHONE);
  await shot(await guest.newPage(), 'manual/images/login.png', '/login');
  await guest.close();

  // 初回ログインの人はパスワード変更画面へ移動する
  const first = await login(browser, 'taro', PHONE);
  await first.page.waitForURL('**/password');
  await shot(first.page, 'manual/images/password.png');
  await first.context.close();

  const { context, page } = await login(browser, 'hanako', PHONE);
  await shot(page, 'manual/images/home.png', '/', { fullPage: true });
  await page.click('[data-nav-open]');
  // メニューが開くアニメーション（200ms）を待つ
  await page.waitForTimeout(500);
  await shot(page, 'manual/images/menu.png');
  await shot(page, 'manual/images/requests.png', `/requests?month=${nextMonth(TODAY)}`);
  await shot(page, 'manual/images/patterns.png', '/mypage/patterns', { fullPage: true });
  await shot(page, 'manual/images/shifts.png', `/shifts?date=${addDays(TODAY, 2)}`);
  await context.close();
}

async function adminShots(browser) {
  const cycleStart = nextCycleStart(TODAY);
  const { context, page } = await login(browser, 'admin', PC);
  await shot(page, 'admin/manual/images/positions.png', '/admin/positions', { fullPage: true });
  await shot(page, 'admin/manual/images/staff-list.png', '/admin/staff', { fullPage: true });
  await shot(page, 'admin/manual/images/staff-new.png', '/admin/staff/new', { fullPage: true });
  // id=3 は山田 花子（demo-data.sql）
  await shot(page, 'admin/manual/images/staff-edit.png', '/admin/staff/3/edit', { fullPage: true });
  await shot(page, 'admin/manual/images/requests.png', `/admin/requests?date=${cycleStart}`);
  await shot(page, 'admin/manual/images/request-edit.png',
    `/admin/requests/edit?userId=3&date=${cycleStart}`, { fullPage: true });
  await shot(page, 'admin/manual/images/shifts.png', `/admin/shifts?date=${addDays(TODAY, 1)}`, { fullPage: true });
  await shot(page, 'admin/manual/images/shifts-reflect.png', `/admin/shifts?date=${addDays(TODAY, 9)}`);
  await shot(page, 'admin/manual/images/settings.png', '/admin/settings');
  await context.close();
}

async function pdf(browser, loginId, url, relativePath) {
  const { context, page } = await login(browser, loginId, PC);
  await page.goto(BASE + url, { waitUntil: 'networkidle' });
  save(relativePath, await page.pdf({
    format: 'A4',
    printBackground: true,
    margin: { top: '15mm', bottom: '15mm', left: '12mm', right: '12mm' },
  }));
  await context.close();
}

async function main() {
  console.log(`今日の日付：${TODAY}`);
  if (await isUp()) throw new Error(`ポート${PORT}は使用中です。起動中のアプリを止めてから実行してください`);
  checkDatabase();

  psql('postgres', ['-c', `DROP DATABASE IF EXISTS ${DB} WITH (FORCE)`]);
  psql('postgres', ['-c', `CREATE DATABASE ${DB}`]);
  execSync('npm run build', { stdio: 'inherit' });
  mkdirSync('target', { recursive: true });

  const app = startApp();
  try {
    await waitForApp(app);
    psql(DB, ['-v', `pw=${PASSWORD}`], readFileSync('scripts/manual/demo-data.sql'));
    const browser = await chromium.launch({ channel: 'msedge' });
    try {
      await staffShots(browser);
      await adminShots(browser);
      await pdf(browser, 'hanako', '/help', 'manual/staff.pdf');
      await pdf(browser, 'admin', '/admin/help', 'admin/manual/admin.pdf');
    } finally {
      await browser.close();
    }
  } finally {
    stopApp(app);
  }
  console.log('完了しました。画像とPDFを確認してからコミットしてください');
}

main().catch((error) => {
  console.error(`失敗しました：${error.message}`);
  process.exitCode = 1;
});
