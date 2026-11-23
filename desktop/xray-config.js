'use strict';

// Build an xray-core config from a parsed LionBox profile.
// Profile fields match what the shared web UI parser produces:
//   VLESS:        { address, port, uuid, security, sni, protocol: 'VLESS' }
//   Shadowsocks:  { address, port, security: method, password, protocol: 'Shadowsocks' }
function profileToXrayConfig(profile, socksPort) {
  const inbound = {
    listen: '127.0.0.1',
    port: socksPort,
    protocol: 'socks',
    settings: { udp: true, auth: 'noauth' },
    sniffing: { enabled: true, destOverride: ['http', 'tls'] }
  };

  let outbound;
  if (profile.protocol === 'Shadowsocks') {
    outbound = {
      protocol: 'shadowsocks',
      settings: {
        servers: [{
          address: profile.address,
          port: Number(profile.port),
          method: profile.security,
          password: profile.password,
          ota: false,
          level: 0
        }]
      }
    };
  } else {
    const tlsSettings = ['tls', 'reality'].includes(profile.security)
      ? {
          allowInsecure: false,
          serverName: profile.sni || profile.address,
          fingerprint: 'chrome'
        }
      : undefined;
    outbound = {
      protocol: 'vless',
      settings: {
        vnext: [{
          address: profile.address,
          port: Number(profile.port),
          users: [{
            id: profile.uuid,
            encryption: 'none',
            flow: '',
            level: 0
          }]
        }]
      },
      streamSettings: {
        network: 'tcp',
        security: profile.security || 'none',
        tlsSettings
      }
    };
  }

  return {
    log: { loglevel: 'warning' },
    inbounds: [inbound],
    outbounds: [outbound, { protocol: 'freedom', tag: 'direct' }]
  };
}

module.exports = { profileToXrayConfig };
