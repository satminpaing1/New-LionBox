'use strict';

// Download the matching xray-core release binary for the current platform
// into desktop/core/. Run once before packaging: npm run fetch-core
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const VERSION = 'v25.9.11'; // xray-core release; bump to update the bundled core

function platformTarget() {
  if (process.platform === 'win32') return 'windows-64';
  if (process.platform === 'darwin') return process.arch === 'arm64' ? 'macos-arm64-v8a' : 'macos-64';
  if (process.platform === 'linux') return process.arch === 'arm64' ? 'linux-arm64-v8a' : 'linux-64';
  throw new Error('Unsupported platform: ' + process.platform);
}

const outDir = path.join(__dirname, 'core');
const zipName = 'Xray-' + platformTarget() + '.zip';
const zipPath = path.join(outDir, zipName);
fs.mkdirSync(outDir, { recursive: true });

const url = 'https://github.com/XTLS/Xray-core/releases/download/' + VERSION + '/' + zipName;
console.log('Downloading ' + url);
execFileSync('curl', ['-fL', '-o', zipPath, url], { stdio: 'inherit' });
const members = ['xray', 'xray.exe', 'geoip.dat', 'geosite.dat'];
const list = execFileSync('unzip', ['-Z1', zipPath], { cwd: outDir, encoding: 'utf8' }).split(/\r?\n/);
execFileSync('unzip', ['-o', zipPath, ...members.filter((m) => list.includes(m))], { cwd: outDir, stdio: 'inherit' });
if (fs.existsSync(path.join(outDir, 'xray'))) fs.chmodSync(path.join(outDir, 'xray'), 0o755);
fs.rmSync(zipPath);
console.log('xray-core ' + VERSION + ' ready in ' + outDir);
