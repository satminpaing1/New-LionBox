'use strict';

const { app, BrowserWindow, ipcMain, nativeTheme, shell, Menu, screen } = require('electron');
const path = require('path');
const fs = require('fs');

const { Connection } = require('./bridge');

const isMac = process.platform === 'darwin';
let win = null;

// Window state persistence: bounds are restored on next launch so the app
// reopens where the user left it.
function windowStateFile() {
  return path.join(app.getPath('userData'), 'window-state.json');
}

function loadWindowState() {
  try {
    const s = JSON.parse(fs.readFileSync(windowStateFile(), 'utf8'));
    if (!Number.isInteger(s.width) || !Number.isInteger(s.height)) return { width: 1200, height: 820 };
    const state = { width: s.width, height: s.height, maximized: !!s.maximized };
    if (Number.isInteger(s.x) && Number.isInteger(s.y)
        && screen.getAllDisplays().some(d =>
          s.x >= d.workArea.x - 40 && s.x < d.workArea.x + d.workArea.width
          && s.y >= d.workArea.y - 40 && s.y < d.workArea.y + d.workArea.height)) {
      state.x = s.x;
      state.y = s.y;
    }
    return state;
  } catch {
    return { width: 1200, height: 820 };
  }
}

function saveWindowState() {
  if (!win || win.isDestroyed()) return;
  try {
    fs.writeFileSync(windowStateFile(), JSON.stringify({
      ...win.getBounds(),
      maximized: win.isMaximized()
    }));
  } catch { /* persistence is best-effort */ }
}
const connection = new Connection();
connection.onState((snapshot) => {
  if (win && !win.isDestroyed()) win.webContents.send('lionbox:state', snapshot);
});

function createWindow() {
  const state = loadWindowState();
  win = new BrowserWindow({
    ...state,
    x: state.x,
    y: state.y,
    minWidth: 940,
    minHeight: 640,
    title: 'LionBox',
    backgroundColor: nativeTheme.shouldUseDarkColors ? '#14100A' : '#F6F3EE',
    autoHideMenuBar: !isMac,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  if (state.maximized) win.maximize();
  win.loadFile(path.join(__dirname, 'ui', 'index.html'));
  win.on('close', saveWindowState);
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://')) shell.openExternal(url);
    return { action: 'deny' };
  });
}

function buildMenu() {
  const template = [
    ...(isMac ? [{
      label: app.name,
      submenu: [
        { role: 'about' },
        { type: 'separator' },
        { role: 'hide' },
        { role: 'unhide' },
        { type: 'separator' },
        { label: 'Quit LionBox', accelerator: 'Cmd+Q', click: () => app.quit() }
      ]
    }] : [{
      label: 'File',
      submenu: [{ label: 'Quit', accelerator: 'Ctrl+Q', click: () => app.quit() }]
    }]),
    {
      label: 'Connection',
      submenu: [{
        label: 'Refresh System Proxy',
        accelerator: isMac ? 'Cmd+R' : 'Ctrl+R',
        click: () => refreshWininet()
      }]
    },
    {
      label: 'View',
      submenu: [
        { role: 'zoomIn' },
        { role: 'zoomOut' },
        { role: 'resetZoom' },
        { type: 'separator' },
        { role: 'togglefullscreen' },
        { role: 'toggleDevTools' }
      ]
    },
    { role: 'windowMenu' }
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

// Refresh the Windows WinINET proxy settings so the system picks up the change immediately.
function refreshWininet() {
  if (process.platform !== 'win32') return;
  // InternetSetOption(INTERNET_OPTION_SETTINGS_CHANGED / REFRESH) via rundll-free ctypes equivalent
  try {
    const { execFile } = require('child_process');
    // powershell P/Invoke is the reliable way without a native module
    execFile('powershell.exe', ['-NoProfile', '-Command', `
      Add-Type -Name wininet -Namespace native -MemberDefinition '
        [DllImport("wininet.dll", SetLastError=true)]
        public static extern bool InternetSetOption(IntPtr hInternet, int dwOption, IntPtr lpBuffer, int dwBufferLength);'
      [native.wininet]::InternetSetOption([IntPtr]::Zero, 39, [IntPtr]::Zero, 0)
      [native.wininet]::InternetSetOption([IntPtr]::Zero, 37, [IntPtr]::Zero, 0)`]);
  } catch (e) { /* proxy refresh is best-effort; settings are still written */ }
}

ipcMain.handle('lionbox:connect', async (_e, profile) => {
  const snapshot = await connection.start(profile);
  if (snapshot.state === 'connected') refreshWininet();
  return snapshot;
});
ipcMain.handle('lionbox:disconnect', async () => {
  const snapshot = await connection.stop();
  refreshWininet();
  return snapshot;
});
ipcMain.handle('lionbox:status', () => ({
  state: connection.state,
  server: connection.profile ? connection.profile.address : null,
  startedAt: connection.startedAt,
  error: connection.error || null
}));
ipcMain.handle('lionbox:platform', () => ({ platform: process.platform, version: app.getVersion() }));

app.whenReady().then(() => { buildMenu(); createWindow(); });
app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
app.on('window-all-closed', () => { if (!isMac) app.quit(); });
app.on('before-quit', () => { connection.stop(); });
