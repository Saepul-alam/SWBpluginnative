#!/usr/bin/env node

/**
 * ============================================================================
 * SWB UPDATER ZERO-CONFIG AUTO-INSTALLER
 * ============================================================================
 * Script ini berjalan otomatis saat user menjalankan:
 *   npm install swbplugin
 *
 * Tugasnya:
 * 1. Otomatis menambahkan script "ota" ke package.json proyek.
 * 2. Otomatis membuat src/boot/updater.js jika proyek Quasar.
 * 3. Otomatis mendaftarkan 'updater' ke boot array di quasar.conf.js.
 * ============================================================================
 */

const fs = require('fs')
const path = require('path')

// INIT_CWD adalah folder proyek utama tempat developer menjalankan "npm install"
const projectDir = process.env.INIT_CWD || process.cwd()

// Jangan jalankan jika sedang install di dalam repo plugin itu sendiri
const myDir = path.resolve(__dirname, '..')
if (projectDir === myDir || projectDir.endsWith('/swbupdater') || projectDir.endsWith('/swbplugin')) {
  process.exit(0)
}

const targetPkgPath = path.join(projectDir, 'package.json')
if (!fs.existsSync(targetPkgPath)) {
  process.exit(0)
}

console.log('\n🚀 [SwbUpdater] Menjalankan auto-setup Zero-Configuration...')

// 1. Otomatis tambahkan scripts OTA ke package.json proyek
try {
  const pkg = JSON.parse(fs.readFileSync(targetPkgPath, 'utf8'))
  if (!pkg.scripts) pkg.scripts = {}

  let modified = false
  if (!pkg.scripts.ota) {
    pkg.scripts.ota = 'ota'
    pkg.scripts['ota:status'] = 'ota status'
    pkg.scripts['ota:build'] = 'ota build'
    pkg.scripts['ota:rollback'] = 'ota rollback'
    modified = true
  }

  if (modified) {
    fs.writeFileSync(targetPkgPath, JSON.stringify(pkg, null, 2), 'utf8')
    console.log('  ✅ Otomatis menambahkan script "ota" ke package.json proyek')
  }
} catch (err) {
  // Diamkan jika gagal baca package.json
}

// 2. Otomatis pasang boot file jika proyek Quasar
const quasarConfPath = path.join(projectDir, 'quasar.conf.js')
if (fs.existsSync(quasarConfPath)) {
  try {
    const bootDir = path.join(projectDir, 'src/boot')
    if (!fs.existsSync(bootDir)) {
      fs.mkdirSync(bootDir, { recursive: true })
    }

    const updaterBootPath = path.join(bootDir, 'updater.js')
    if (!fs.existsSync(updaterBootPath)) {
      const template = `import { SwbUpdater } from 'swbupdater'
import { Dialog, Loading, Notify } from 'quasar'

let cachedFeatures = {}

export default async ({ Vue }) => {
  // Helper saklar fitur di template: v-if="$feature('payment.qris')"
  Vue.prototype.$feature = (name) => cachedFeatures[name] ?? true

  if (typeof window === 'undefined' || !window.Capacitor || window.Capacitor.platform === 'web') return

  try {
    cachedFeatures = await SwbUpdater.getFeatures()
  } catch (_) {}

  setTimeout(async () => {
    try {
      const check = await SwbUpdater.checkForUpdate()
      if (check && check.available) {
        Dialog.create({
          title: 'Pembaruan Tersedia',
          message: \`Versi baru (Build \${check.remoteBuild}) tersedia. Perbarui sekarang?\`,
          ok: 'Perbarui Sekarang',
          cancel: 'Nanti'
        }).onOk(async () => {
          Loading.show({ message: 'Mengunduh dan menerapkan update...' })
          try {
            await SwbUpdater.sync()
          } catch (err) {
            Loading.hide()
            Notify.create({ type: 'negative', message: 'Gagal update: ' + err.message })
          }
        })
      }
    } catch (_) {}
  }, 1500)
}
`
      fs.writeFileSync(updaterBootPath, template, 'utf8')
      console.log('  ✅ Otomatis membuat file src/boot/updater.js')
    }

    // Daftarkan ke quasar.conf.js jika belum ada
    let confContent = fs.readFileSync(quasarConfPath, 'utf8')
    if (!confContent.includes("'updater'") && !confContent.includes('"updater"')) {
      confContent = confContent.replace(/boot\s*:\s*\[([\s\S]*?)\]/, (match, p1) => {
        const trimmed = p1.trim()
        if (!trimmed) return `boot: [\n      'updater'\n    ]`
        return `boot: [\n      'updater',${p1}`
      })
      fs.writeFileSync(quasarConfPath, confContent, 'utf8')
      console.log("  ✅ Otomatis mendaftarkan 'updater' di quasar.conf.js")
    }
  } catch (err) {
    // Diamkan jika gagal setup Quasar
  }
}

console.log('✨ [SwbUpdater] Setup selesai otomatis! Langsung siap pakai.\n')

