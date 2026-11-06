# Dokumentasi Plugin SwbPlugin (Native GPS, Tracking & Media)

Plugin Capacitor untuk aplikasi Wahana: proteksi anti-Fake GPS, background location tracking, upload posisi otomatis, dan native camera/media picker.

---

## Cara Instalasi di Project

### 1. Masuk ke folder project
Masuk ke root directory aplikasi Anda:
```bash
cd /opt/pm/apps/swb/web/dev/dcaf
```

### 2. Install Plugin
Jalankan command install langsung dari Git branch `native`:
```bash
npm install https://github.com/Saepul-alam/SWBpluginnative.git#native
```

### 3. Sinkronkan ke Android
Jalankan command sinkronisasi Capacitor:
```bash
npx cap sync android
```
*Pada Capacitor 3+, plugin otomatis terhubung ke native Android tanpa perlu edit file Java/Kotlin di `MainActivity`.*

---

## Cara Pakai di Kodingan

### 1. Cek Anti-Fake GPS (Mock Location)
```javascript
import { SwbPlugin } from 'swbplugin'

const res = await SwbPlugin.checkMock()
if (res.isMock) {
  alert('Fake GPS terdeteksi! Harap matikan aplikasi Fake GPS.')
}
```

### 2. Jalankan Background Tracking Lokasi
```javascript
import { SwbPlugin } from 'swbplugin'

await SwbPlugin.startTracking({
  trackingMode: 'balance',
  interval: 10000, // interval 10 detik
  uploadUrl: 'https://swb.wahana.com/a/location'
})
```

### 3. Ambil Foto dari Kamera Asli
```javascript
import { SwbPlugin } from 'swbplugin'

const photo = await SwbPlugin.takePhoto({
  quality: 80,
  allowEditing: false
})
console.log('Path foto:', photo.path)
```

---

## Fix Error & Troubleshooting

### 1. Lokasi tidak terkirim saat aplikasi di-minimize
- Pastikan izin "Allow all the time" (Izinkan sepanjang waktu) sudah diberikan pada menu Pengaturan HP pengguna.
- Pastikan HP tidak membatasi battery saver (Doze mode) untuk aplikasi Wahana.

### 2. Error Fake GPS terus muncul padahal tidak pakai Fake GPS
- Buka Opsi Pengembang (Developer Options) di HP Android.
- Pastikan menu "Select mock location app" dalam kondisi "Nothing" (Tidak ada).
