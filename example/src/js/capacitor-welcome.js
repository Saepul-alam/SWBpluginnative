import { SplashScreen } from '@capacitor/splash-screen';
import { Camera } from '@capacitor/camera';
import { Capacitor } from '@capacitor/core';
import { SwbUpdater } from 'swbupdater';

window.customElements.define(
  'capacitor-welcome',
  class extends HTMLElement {
    constructor() {
      super();

      SplashScreen.hide();

      const root = this.attachShadow({ mode: 'open' });

      root.innerHTML = `
    <style>
      :host {
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        display: block;
        width: 100%;
        height: 100%;
      }
      h1, h2, h3, h4, h5 {
        text-transform: uppercase;
      }
      .button {
        display: inline-block;
        padding: 10px 14px;
        background-color: #73B5F6;
        color: #fff;
        font-size: 0.9em;
        border: 0;
        border-radius: 4px;
        text-decoration: none;
        cursor: pointer;
      }
      .button:hover {
        opacity: 0.9;
      }
      .button-danger {
        background-color: #e06c75;
      }
      .button-secondary {
        background-color: #4b5263;
      }
      main {
        padding: 15px;
        max-width: 600px;
        margin: 0 auto;
      }
      main hr { height: 1px; background-color: #eee; border: 0; margin: 20px 0; }
      main h1 {
        font-size: 1.4em;
        text-transform: uppercase;
        letter-spacing: 1px;
      }
      main h2 {
        font-size: 1.1em;
      }
      main p {
        color: #333;
        line-height: 1.5;
      }
      pre {
        white-space: pre-wrap;
        word-break: break-all;
      }
      .button-group {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
        margin-top: 10px;
        margin-bottom: 12px;
      }
      .panel {
        background: #282c34;
        color: #abb2bf;
        padding: 12px;
        border-radius: 6px;
        font-size: 0.85em;
        font-family: monospace;
      }
      .log-panel {
        background: #1e1e1e;
        color: #98c379;
        max-height: 160px;
        overflow-y: auto;
      }
      .platform-badge {
        display: inline-block;
        padding: 2px 8px;
        border-radius: 12px;
        font-size: 0.75em;
        font-weight: bold;
        background: #e5c07b;
        color: #000;
        margin-left: 8px;
      }
    </style>
    <div>
      <capacitor-welcome-titlebar>
        <h1>SwbUpdater Example</h1>
      </capacitor-welcome-titlebar>
      <main>
        <h2>Live Update (OTA) <span class="platform-badge" id="platform-badge">Detecting...</span></h2>
        <p>
          Uji coba plugin OTA Live Update web aset pada Capacitor 3 (khusus platform Android).
          URL manifest default di konfigurasi adalah placeholder (<code>https://example.com/ota/manifest.json</code>).
        </p>

        <div class="button-group">
          <button class="button" id="btn-check-update">Check Update</button>
          <button class="button" id="btn-sync-update">Sync Update</button>
          <button class="button button-secondary" id="btn-get-bundle">Current Bundle</button>
          <button class="button button-danger" id="btn-reset-builtin">Reset to Builtin</button>
        </div>

        <div>
          <h4>Status / Hasil Operasi:</h4>
          <pre class="panel" id="ota-status">Tekan salah satu tombol di atas untuk memulai.</pre>
        </div>

        <div>
          <h4>Log Event (updateStateChanged):</h4>
          <pre class="panel log-panel" id="ota-logs">[Menunggu event dari plugin native...]</pre>
        </div>

        <hr />
        <h2>Plugin Asli & Kamera</h2>
        <p>
          <button class="button" id="take-photo">Take Photo</button>
        </p>
        <p>
          <img id="image" style="max-width: 100%">
        </p>
      </main>
    </div>
    `;
    }

    connectedCallback() {
      const self = this;
      const root = self.shadowRoot;
      const platform = Capacitor.getPlatform();

      const platformBadge = root.querySelector('#platform-badge');
      if (platformBadge) {
        platformBadge.textContent = platform.toUpperCase();
        if (platform === 'android') {
          platformBadge.style.backgroundColor = '#98c379';
        } else {
          platformBadge.style.backgroundColor = '#e06c75';
          platformBadge.style.color = '#fff';
        }
      }

      const statusEl = root.querySelector('#ota-status');
      const logsEl = root.querySelector('#ota-logs');

      const setStatus = text => {
        if (statusEl) {
          statusEl.textContent = text;
        }
      };

      const appendLog = logText => {
        if (logsEl) {
          const timestamp = new Date().toLocaleTimeString();
          logsEl.textContent += `\n[${timestamp}] ${logText}`;
          logsEl.scrollTop = logsEl.scrollHeight;
        }
      };

      // Daftarkan listener updateStateChanged hanya satu kali
      if (platform === 'android') {
        SwbUpdater.addListener('updateStateChanged', event => {
          appendLog(
            `STATE: ${event.state} | ${event.message || ''} ${
              event.build ? `(build: ${event.build})` : ''
            }`,
          );
        }).catch(err => {
          appendLog(`Gagal mendaftarkan listener: ${err.message}`);
        });
      } else {
        appendLog(
          `Platform saat ini '${platform}'. Fitur OTA native SwbUpdater hanya aktif di Android.`,
        );
      }

      // Check Update
      root
        .querySelector('#btn-check-update')
        .addEventListener('click', async () => {
          if (platform !== 'android') {
            setStatus(
              `Operasi dibatalkan: Platform '${platform}' tidak didukung oleh SwbUpdater native. Silakan jalankan di perangkat Android.`,
            );
            return;
          }
          setStatus('Memeriksa pembaruan ke server manifest...');
          try {
            const result = await SwbUpdater.checkForUpdate();
            setStatus(`Hasil Pemeriksaan:\n` + JSON.stringify(result, null, 2));
          } catch (e) {
            setStatus(
              `Pemeriksaan GAGAL (Rejected):\n${e.message || String(e)}`,
            );
          }
        });

      // Sync Update
      root
        .querySelector('#btn-sync-update')
        .addEventListener('click', async () => {
          if (platform !== 'android') {
            setStatus(
              `Operasi dibatalkan: Platform '${platform}' tidak didukung oleh SwbUpdater native.`,
            );
            return;
          }
          setStatus('Memulai sinkronisasi dan instalasi update...');
          try {
            const result = await SwbUpdater.sync();
            setStatus(
              `Sinkronisasi Berhasil:\n` + JSON.stringify(result, null, 2),
            );
          } catch (e) {
            setStatus(
              `Sinkronisasi GAGAL (Rejected):\n${e.message || String(e)}`,
            );
          }
        });

      // Current Bundle
      root
        .querySelector('#btn-get-bundle')
        .addEventListener('click', async () => {
          setStatus('Mengambil info bundle aktif saat ini...');
          try {
            const bundle = await SwbUpdater.getCurrentBundle();
            setStatus(
              `Bundle Aktif Saat Ini:\n` + JSON.stringify(bundle, null, 2),
            );
          } catch (e) {
            setStatus(
              `Gagal mengambil bundle aktif:\n${e.message || String(e)}`,
            );
          }
        });

      // Reset to Builtin
      root
        .querySelector('#btn-reset-builtin')
        .addEventListener('click', async () => {
          if (platform !== 'android') {
            setStatus(
              `Operasi dibatalkan: Platform '${platform}' tidak didukung oleh SwbUpdater native.`,
            );
            return;
          }
          setStatus('Mereset bundle ke bawaan APK (public asset)...');
          try {
            await SwbUpdater.resetToBuiltin();
            setStatus(
              'Reset berhasil dijadwalkan. WebView akan memuat aset bawaan.',
            );
          } catch (e) {
            setStatus(`Reset GAGAL (Rejected):\n${e.message || String(e)}`);
          }
        });

      // Kamera Demo
      root.querySelector('#take-photo').addEventListener('click', async () => {
        try {
          const photo = await Camera.getPhoto({
            resultType: 'uri',
          });
          const image = root.querySelector('#image');
          if (image) {
            image.src = photo.webPath;
          }
        } catch (e) {
          console.warn('User cancelled', e);
        }
      });
    }
  },
);

window.customElements.define(
  'capacitor-welcome-titlebar',
  class extends HTMLElement {
    constructor() {
      super();
      const root = this.attachShadow({ mode: 'open' });
      root.innerHTML = `
    <style>
      :host {
        position: relative;
        display: block;
        padding: 15px;
        text-align: center;
        background-color: #73B5F6;
      }
      ::slotted(h1) {
        margin: 0;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        font-size: 1em;
        font-weight: 600;
        color: #fff;
      }
    </style>
    <slot></slot>
    `;
    }
  },
);
