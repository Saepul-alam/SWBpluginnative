# Dokumentasi Plugin SwbUpdater (Live Update & Feature Management)

Plugin Capacitor Android untuk Live Update (OTA) aset web dan manajemen saklar fitur (Feature Flags) aplikasi Wahana.

---

## Cara Instalasi di Project

### 1. Masuk ke folder project
Masuk ke root directory aplikasi Anda:
```bash
cd /opt/pm/apps/swb/web/dev/dcaf
```

### 2. Install Plugin
Jalankan command install langsung dari Git:
```bash
npm install https://github.com/Saepul-alam/SWBpluginnative.git#updater
```
*Plugin ini sudah zero-configuration (auto-setup). Script `ota`, boot file `src/boot/updater.js`, dan konfigurasi `quasar.conf.js` otomatis dibuatkan.*

### 3. Sinkronkan ke Android
Jalankan command sinkronisasi Capacitor:
```bash
npx cap sync android
```

---

## Cara Pakai di Kodingan

### 1. Cek Update Otomatis saat Buka Aplikasi
File `src/boot/updater.js` sudah otomatis dibuat dan didaftarkan di `quasar.conf.js`. Saat pengguna membuka aplikasi, pop-up pembaruan akan otomatis muncul jika ada build baru.

### 2. Memakai Saklar Fitur (Feature Flag) di Vue
Di file `.vue` mana pun, cukup gunakan helper `$feature`:
```html
<!-- Tombol hanya muncul jika fitur aktif untuk pengguna tersebut -->
<q-btn v-if="$feature('payment.qris')" label="Bayar QRIS" color="positive" icon="qr_code" />
```

### 3. Mengatur Konteks Pengguna (User Context) saat Login
Di halaman login (misal `Login.vue`), panggil fungsi ini saat user berhasil login agar plugin tahu cabang dan user ID-nya:
```javascript
import { setUserContext } from 'src/boot/updater'

await setUserContext({
  userId: '1024',
  branch: 'HUB-JKT'
})
```

---

## Langkah Build & Rilis OTA (SOP Rilis)

Setiap selesai koding fitur baru di web dan ingin merilis ke aplikasi tanpa perlu membuat file APK baru:

### 1. Jalankan Command Build
Di terminal folder project aplikasi, jalankan:
```bash
npm run ota:build
```
*Script otomatis meng-compile Quasar web, membuat `dist/ota/bundle.zip`, menghitung checksum SHA-256, dan memperbarui `manifest.json`.*

### 2. Upload Hasil Build ke Server
File hasil build ada di folder `dist/ota`. Upload kedua file ini ke direktori server Nginx:
- `dist/ota/bundle.zip`    -> `https://swb-rc.wahana.com/ota/bundle.zip`
- `dist/ota/manifest.json` -> `https://swb-rc.wahana.com/ota/manifest.json`

Selesai! Seluruh pengguna yang membuka aplikasi akan langsung menerima update.

---

## Manajemen Fitur via CLI (Feature Flags)

Jalankan perintah berikut di terminal project:

### 1. Buka Menu Interaktif (Wizard)
```bash
npm run ota
```

### 2. Cek Status Build & Daftar Fitur
```bash
npm run ota status
```

### 3. Buka Fitur Khusus User Tertentu (Uji Coba / Whitelist)
```bash
node scripts/ota-cli.js feature:set payment.qris --enable --users 1024,1025 --branch HUB-JKT
```

### 4. Jadwalkan Rilis di Tanggal Tertentu
```bash
node scripts/ota-cli.js feature:set delivery.batch_scan --enable --date "2026-11-20 08:00"
```

### 5. Matikan Fitur Seketika (Emergency Kill-Switch jika Backend Down)
```bash
node scripts/ota-cli.js feature:disable payment.qris
```

### 6. Emergency Rollback Versi Aplikasi
```bash
npm run ota:rollback
```

---

## Ubah Konfigurasi Server (Opsional)

Jika server produksi berbeda dengan default (`swb-rc.wahana.com`), sesuaikan file `src-capacitor/capacitor.config.json`:
```json
{
  "plugins": {
    "SwbUpdater": {
      "updateUrl": "https://swb.wahana.com/ota/manifest.json",
      "autoUpdate": false
    }
  }
}
```

---

## Fix Error & Troubleshooting

### 1. Error: "updateUrl tidak valid / gagal download manifest"
- Pastikan koneksi internet HP pengguna aktif.
- Pastikan URL menggunakan protokol `HTTPS` (bukan HTTP).
- Cek URL manifest dapat diakses melalui browser: `https://swb-rc.wahana.com/ota/manifest.json`.

### 2. Aplikasi crash atau layar putih setelah update
- Sistem memiliki **Self-Healing Otomatis**: jika bundle baru crash saat pertama kali buka, native Android otomatis membatalkan bundle baru dan kembali ke versi bawaan APK (`builtin`).
- Jalankan `npm run ota:rollback` di terminal untuk mengembalikan manifest server ke build stabil sebelumnya.

### 3. Backend fitur baru error (HTTP 500 / Timeout)
- Tidak perlu rebuild APK atau frontend. Cukup matikan fitur tersebut dalam 1 detik via CLI:
  ```bash
  node scripts/ota-cli.js feature:disable <nama_fitur>
  ```
