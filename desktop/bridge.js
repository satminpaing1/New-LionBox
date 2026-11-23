'use strict';

// Connection manager: spawns xray-core with the generated config and sets the
// OS system proxy to the local SOCKS port. Windows uses reg.exe, macOS uses
// networksetup; both changes are reverted on disconnect and app quit.
const { spawn, execFile } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { profileToXrayConfig } = require('./xray-config');

const SOCKS_PORT = 10808;

function coreBinary() {
  const name = process.platform === 'win32' ? 'xray.exe' : 'xray';
  const local = path.join(__dirname, 'core', name);
  if (fs.existsSync(local)) return local;
  // packaged app: extraResources copies core/ next to the app
  const packaged = path.join(process.resourcesPath, 'core', name);
  if (fs.existsSync(packaged)) return packaged;
  return null;
}

function run(cmd, args) {
  return new Promise((resolve) => {
    execFile(cmd, args, (err, stdout) => resolve({ err, stdout: String(stdout || '').trim() }));
  });
}

function networkServicesMac() {
  return run('networksetup', ['-listallnetworkservices']).then(({ stdout }) =>
    stdout.split('\n').slice(1).map(s => s.trim()).filter(s => s && !s.startsWith('*') && !s.startsWith('An asterisk'))
  );
}

const proxy = {
  async enable() {
    if (process.platform === 'win32') {
      // User-scoped Internet Settings for the current user; Windows picks it up
      // on the next WinINET refresh (WinINET refresh is broadcast via InternetSetOption in main.js).
      for (const [name, value, type] of [
        ['ProxyEnable', '1', 'REG_DWORD'],
        ['ProxyServer', 'socks=127.0.0.1:' + SOCKS_PORT, 'REG_SZ'],
        ['ProxyOverride', '<local>', 'REG_SZ']
      ]) {
        await run('reg', ['add', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings', '/v', name, '/t', type, '/d', value, '/f']);
      }
    } else if (process.platform === 'darwin') {
      const { services } = { services: await networkServicesMac() };
      for (const service of services) {
        await run('networksetup', ['-setsocksfirewallproxy', service, '127.0.0.1', String(SOCKS_PORT)]);
      }
    }
  },
  async disable() {
    if (process.platform === 'win32') {
      await run('reg', ['add', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings', '/v', 'ProxyEnable', '/t', 'REG_DWORD', '/d', '0', '/f']);
    } else if (process.platform === 'darwin') {
      for (const service of await networkServicesMac()) {
        await run('networksetup', ['-setsocksfirewallproxystate', service, 'off']);
      }
    }
  }
};

class Connection {
  constructor() {
    this.process = null;
    this.configPath = null;
    this.state = 'off'; // off | connecting | connected
    this.startedAt = null;
    this.profile = null;
    this.listeners = new Set();
  }

  onState(listener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  emit(extra) {
    const snapshot = {
      state: this.state,
      server: this.profile ? this.profile.address : null,
      socksPort: SOCKS_PORT,
      startedAt: this.startedAt,
      error: this.error || null,
      ...extra
    };
    this.listeners.forEach(fn => fn(snapshot));
    return snapshot;
  }

  async start(profile) {
    if (this.process) return this.emit();
    const bin = coreBinary();
    if (!bin) throw new Error('xray-core binary not found. Run: npm run fetch-core');

    this.state = 'connecting';
    this.error = null;
    this.profile = profile;
    this.emit();

    this.configPath = path.join(os.tmpdir(), 'lionbox-xray-' + process.pid + '.json');
    fs.writeFileSync(this.configPath, JSON.stringify(profileToXrayConfig(profile, SOCKS_PORT), null, 2));

    await new Promise((resolve, reject) => {
      this.process = spawn(bin, ['run', '-c', this.configPath], { stdio: ['ignore', 'pipe', 'pipe'] });
      let output = '';
      const onData = (chunk) => {
        output += chunk;
        // xray logs "started" once the inbound is accepting connections
        if (this.state === 'connecting' && /started/i.test(output)) {
          this.state = 'connected';
          this.startedAt = Date.now();
          this.emit();
          resolve();
        }
      };
      this.process.stdout.on('data', onData);
      this.process.stderr.on('data', onData);
      this.process.once('error', (err) => { this.process = null; reject(err); });
      this.process.once('exit', (code) => {
        this.process = null;
        if (this.state !== 'off') {
          this.state = 'off';
          this.startedAt = null;
          this.error = code ? 'xray-core exited with code ' + code : null;
          this.emit();
          proxy.disable();
        }
      });
      setTimeout(() => {
        if (this.state === 'connecting') {
          this.state = 'connected'; // core may log nothing when config is valid and quiet
          this.startedAt = Date.now();
          this.emit();
          resolve();
        }
      }, 4000);
    });

    await proxy.enable();
    return this.emit();
  }

  async stop() {
    if (this.process) {
      const proc = this.process;
      this.process = null;
      proc.removeAllListeners('exit');
      proc.kill();
    }
    if (this.configPath && fs.existsSync(this.configPath)) fs.rmSync(this.configPath);
    this.state = 'off';
    this.startedAt = null;
    await proxy.disable();
    return this.emit();
  }
}

module.exports = { Connection, SOCKS_PORT };
