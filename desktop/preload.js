'use strict';

const { contextBridge, ipcRenderer } = require('electron');

// Exposed to the page as window.lionbox — the shared web UI detects this
// to switch from browser-only (demo) mode to the real desktop engine.
contextBridge.exposeInMainWorld('lionbox', {
  isDesktop: true,
  connect: (profile) => ipcRenderer.invoke('lionbox:connect', profile),
  disconnect: () => ipcRenderer.invoke('lionbox:disconnect'),
  status: () => ipcRenderer.invoke('lionbox:status'),
  platform: () => ipcRenderer.invoke('lionbox:platform'),
  ping: (profile) => ipcRenderer.invoke('lionbox:ping', profile),
  onState: (cb) => {
    // main pushes snapshots; simple polling-free wiring via repeated status is
    // avoided by listening once on the connection snapshot channel
    const listener = (_e, snapshot) => cb(snapshot);
    ipcRenderer.on('lionbox:state', listener);
    return () => ipcRenderer.removeListener('lionbox:state', listener);
  }
});
