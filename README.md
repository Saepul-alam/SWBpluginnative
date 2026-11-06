# Dokumentasi Plugin SwbNotif (Notifikasi Lokal, Terjadwal & Push)

Plugin Capacitor untuk notifikasi lokal, pengingat alarm terjadwal, dan push notification pada aplikasi Wahana.

---

## Cara Instalasi di Project

### 1. Masuk ke folder project
Masuk ke root directory aplikasi Anda:
```bash
cd /opt/pm/apps/swb/web/dev/dcaf
```

### 2. Install Plugin
Jalankan command install langsung dari Git branch `notify`:
```bash
npm install https://github.com/Saepul-alam/SWBpluginnative.git#notify
```

### 3. Sinkronkan ke Android
Jalankan command sinkronisasi Capacitor:
```bash
npx cap sync android
```
*Plugin ini 100% Zero-Configuration untuk notifikasi lokal & terjadwal. Semua izin dan alarm otomatis di-*merge* ke Android.*

---

## Cara Pakai di Kodingan

### 1. Meminta Izin Notifikasi (Android 13+)
```javascript
import { SwbNotif } from 'swbnotif'

const perm = await SwbNotif.requestPermission()
if (!perm.granted) {
  console.warn('Izin notifikasi ditolak oleh pengguna')
}
```

### 2. Menampilkan Notifikasi Langsung / Terjadwal
```javascript
import { SwbNotif } from 'swbnotif'

await SwbNotif.schedule({
  notifications: [
    {
      id: 1,
      title: 'Tugas Pengantaran Baru',
      body: 'Ada 5 paket baru yang harus diantar sekarang.',
      schedule: { at: new Date(Date.now() + 1000 * 5) } // muncul 5 detik lagi
    }
  ]
})
```

---

## Konfigurasi Tambahan (Khusus Remote Push Notification FCM)

Jika ingin menerima Push Notification jarak jauh dari server via Google Firebase Cloud Messaging:
1. Ambil file `google-services.json` dari console Firebase Wahana.
2. Letakkan file tersebut di folder:
   `/opt/pm/apps/swb/web/dev/dcaf/src-capacitor/android/app/google-services.json`

*(Jika hanya untuk notifikasi lokal di HP kurir, langkah ini tidak diperlukan).*

---

## Fix Error & Troubleshooting

### 1. Notifikasi tidak muncul saat HP di-restart
- Plugin sudah memiliki receiver `BOOT_COMPLETED` bawaan yang otomatis memulihkan alarm jadwal. Pastikan aplikasi memiliki izin "Autostart" pada HP merk tertentu (Xiaomi, Oppo, Vivo).

### 2. Suara notifikasi tidak berbunyi
- Pastikan profil nada dering HP tidak dalam mode "Jangan Ganggu" (Do Not Disturb) atau "Hening".
