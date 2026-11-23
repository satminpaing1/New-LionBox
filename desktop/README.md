# LionBox Desktop (Windows & macOS)

Electron app that reuses the exact web UI in `../index.html` + `../app.js` and runs a real
xray-core engine behind it — the same connection model as the Android app.

## Structure

- `main.js` — Electron main process: window, IPC, Windows WinINET proxy refresh
- `preload.js` — exposes `window.lionbox` (connect / disconnect / status / onState)
- `bridge.js` — spawns xray-core, sets system proxy (reg.exe on Windows, networksetup on macOS)
- `xray-config.js` — builds the xray config from the shared VLESS / Shadowsocks profile parser
- `fetch-core.js` — downloads the matching xray-core release into `core/`

## Build & run

```bash
cd desktop
npm install
npm run fetch-core   # download xray-core for this platform (once)
npm start            # run the app
npm run dist:win     # Windows NSIS installer
npm run dist:mac     # macOS dmg (sign with your Apple ID for distribution)
```

Without `core/xray` the app still launches but Connect reports how to fetch the core.

## Platform notes

- Windows: sets user-scoped `ProxyServer=socks=127.0.0.1:10808` and broadcasts a WinINET refresh.
- macOS: `networksetup -setsocksfirewallproxy` on every network service; reverted on disconnect/quit.
- Local SOCKS inbound is `127.0.0.1:10808`, UDP supported; keys stay in browser localStorage as on the web.
