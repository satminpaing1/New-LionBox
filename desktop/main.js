'use strict';

const { app, BrowserWindow, ipcMain, nativeTheme, shell } = require('electron');
const path = require('path');

const { Connection } = require('./bridge');

const isMac = process.platform === 'darwin';
let win = null;
const connection = new Connection();
connection.onState((snapshot) => {
  if (win && !win.isDestroyed()) win.webContents.send('lionbox:state', snapshot);
});

function createWindow() {
  win = new BrowserWindow({
    width: 1200,
    height: 820,
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
  win.loadFile(path.join(__dirname, 'ui', 'index.html'));
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://')) shell.openExternal(url);
    return { action: 'deny' };
  });
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

app.whenReady().then(createWindow);
app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
app.on('window-all-closed', () => { if (!isMac) app.quit(); });
app.on('before-quit', () => { connection.stop(); });
