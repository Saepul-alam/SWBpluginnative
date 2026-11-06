#!/usr/bin/env node

/**
 * ============================================================================
 * SWB OTA RELEASE & FEATURE MANAGEMENT CLI
 * ============================================================================
 * Tool CLI untuk mengelola Live Update (OTA) dan Saklar Fitur (Feature Flags)
 * aplikasi Wahana.
 *
 * Penggunaan:
 *   node scripts/ota-cli.js                      (Mode Interaktif / Wizard)
 *   node scripts/ota-cli.js status               (Cek Status Rilis & Fitur)
 *   node scripts/ota-cli.js build                (Build & Kemas Bundle OTA)
 *   node scripts/ota-cli.js rollback             (Emergency Rollback)
 *   node scripts/ota-cli.js feature:list         (Daftar Semua Fitur)
 *   node scripts/ota-cli.js feature:enable <key> (Nyalakan Fitur)
 *   node scripts/ota-cli.js feature:disable <key>(Matikan Fitur / Kill-Switch)
 *   node scripts/ota-cli.js feature:set <key>    (Atur Target / Jadwal Fitur)
 * ============================================================================
 */

const fs = require('fs')
const path = require('path')
const crypto = require('crypto')
const readline = require('readline')
const { execSync } = require('child_process')

// Path file & direktori
const ROOT_DIR = process.cwd()
const SPA_DIR = path.resolve(ROOT_DIR, 'dist/spa')
const OTA_DIR = path.resolve(ROOT_DIR, 'dist/ota')
const MANIFEST_FILE = path.resolve(OTA_DIR, 'manifest.json')
const BUNDLE_FILE = path.resolve(OTA_DIR, 'bundle.zip')
const PKG_FILE = path.resolve(ROOT_DIR, 'package.json')

// Helper baca/tulis manifest
function ensureOtaDir() {
  if (!fs.existsSync(OTA_DIR)) {
    fs.mkdirSync(OTA_DIR, { recursive: true })
  }
}

function loadManifest() {
  ensureOtaDir()
  if (!fs.existsSync(MANIFEST_FILE)) {
    const defaultManifest = {
      build: 1,
      version: '1.0.0',
      url: 'https://swb-rc.wahana.com/ota/bundle.zip',
      checksum: '0000000000000000000000000000000000000000000000000000000000000000',
      size: 0,
      features: {
        'payment.qris': {
          enabled: false,
          description: 'Pembayaran menggunakan QRIS',
          users: ['user_test_01'],
          branches: ['HUB-JKT'],
          releaseDate: null,
          percentage: 100
        },
        'delivery.batch_scan': {
          enabled: true,
          description: 'Scan Masal 50 Paket Sekaligus',
          users: [],
          branches: [],
          releaseDate: null,
          percentage: 100
        }
      }
    }
    fs.writeFileSync(MANIFEST_FILE, JSON.stringify(defaultManifest, null, 2), 'utf8')
    return defaultManifest
  }

  try {
    const content = fs.readFileSync(MANIFEST_FILE, 'utf8')
    const parsed = JSON.parse(content)
    if (!parsed.features) parsed.features = {}
    return parsed
  } catch (err) {
    console.error('❌ Gagal membaca manifest.json:', err.message)
    process.exit(1)
  }
}

function saveManifest(manifest) {
  ensureOtaDir()
  fs.writeFileSync(MANIFEST_FILE, JSON.stringify(manifest, null, 2), 'utf8')
}

// Helper argumen CLI
const args = process.argv.slice(2)
const command = args[0] || 'menu'

function getArgValue(key, defaultValue = null) {
  const idx = args.indexOf(key)
  if (idx !== -1 && idx + 1 < args.length) {
    return args[idx + 1]
  }
  return defaultValue
}

function hasArg(key) {
  return args.includes(key)
}

// ============================================================================
// COMMAND: STATUS
// ============================================================================
function showStatus() {
  const manifest = loadManifest()
  console.log('\n=================================================================')
  console.log('             📦 STATUS LIVE UPDATE & FITUR (OTA)')
  console.log('=================================================================')
  console.log(`📌 Build Aktif   : ${manifest.build}`)
  console.log(`🏷️  Versi         : ${manifest.version}`)
  console.log(`🌐 URL Bundle    : ${manifest.url}`)
  console.log(`🔑 SHA-256       : ${manifest.checksum.substring(0, 16)}...${manifest.checksum.substring(48)}`)
  console.log(`📊 Ukuran File   : ${(manifest.size / 1024).toFixed(1)} KB`)
  console.log('-----------------------------------------------------------------')
  console.log('                 🚩 DAFTAR STATUS FITUR (FEATURE FLAGS)')
  console.log('-----------------------------------------------------------------')

  const features = manifest.features || {}
  const keys = Object.keys(features)

  if (keys.length === 0) {
    console.log('  (Belum ada fitur yang didaftarkan)')
  } else {
    keys.forEach((key) => {
      const feat = features[key]
      let statusStr = '🟢 AKTIF (100% Pengguna)'
      let details = []

      if (typeof feat === 'boolean') {
        statusStr = feat ? '🟢 AKTIF (100% Pengguna)' : '🔴 NONAKTIF (Kill-Switch)'
      } else {
        if (!feat.enabled) {
          statusStr = '🔴 NONAKTIF (Kill-Switch)'
        } else {
          if (feat.releaseDate) {
            const relTime = new Date(feat.releaseDate).getTime()
            if (Date.now() < relTime) {
              statusStr = `⏳ TERJADWAL (${feat.releaseDate})`
            }
          }

          if (feat.users && feat.users.length > 0) {
            details.push(`User: [${feat.users.join(', ')}]`)
          }
          if (feat.branches && feat.branches.length > 0) {
            details.push(`Cabang: [${feat.branches.join(', ')}]`)
          }
          if (feat.percentage && feat.percentage < 100) {
            details.push(`Rollout: ${feat.percentage}%`)
          }
        }
      }

      console.log(`• [${key}]`)
      if (feat.description) console.log(`  Keterangan : ${feat.description}`)
      console.log(`  Status     : ${statusStr}`)
      if (details.length > 0) {
        console.log(`  Target     : ${details.join(' | ')}`)
      }
      console.log('')
    })
  }
  console.log('=================================================================\n')
}

// ============================================================================
// COMMAND: BUILD & BUNDLE
// ============================================================================
function runBuild() {
  const manifest = loadManifest()
  console.log('\n🚀 Memulai proses pengemasan OTA Bundle...')

  if (!fs.existsSync(SPA_DIR) || !fs.existsSync(path.join(SPA_DIR, 'index.html'))) {
    console.error('❌ Error: Folder "dist/spa/index.html" tidak ditemukan!')
    process.exit(1)
  }

  ensureOtaDir()
  if (fs.existsSync(BUNDLE_FILE)) {
    fs.unlinkSync(BUNDLE_FILE)
  }

  // Kompresi zip dengan index.html tepat di root
  console.log('📦 Mengompresi file ke dist/ota/bundle.zip...')
  try {
    execSync(`cd "${SPA_DIR}" && zip -r -q "${BUNDLE_FILE}" . -x "*.DS_Store"`, { stdio: 'inherit' })
  } catch (err) {
    console.error('❌ Gagal kompresi ZIP:', err.message)
    process.exit(1)
  }

  // Hitung SHA-256 & size
  const zipBuffer = fs.readFileSync(BUNDLE_FILE)
  const checksum = crypto.createHash('sha256').update(zipBuffer).digest('hex')
  const size = zipBuffer.length

  const targetBuild = parseInt(getArgValue('--build', manifest.build + 1), 10)
  const targetVersion = getArgValue('--version', manifest.version)
  const targetUrl = getArgValue('--url', manifest.url)

  manifest.build = targetBuild
  manifest.version = targetVersion
  manifest.url = targetUrl
  manifest.checksum = checksum
  manifest.size = size

  saveManifest(manifest)

  console.log('\n✅ BUNDLE BERHASIL DIBUAT!')
  console.log('--------------------------------------------------')
  console.log(`📌 Build Baru    : ${manifest.build}`)
  console.log(`🏷️  Versi         : ${manifest.version}`)
  console.log(`📁 File Bundle   : ${BUNDLE_FILE} (${(size / 1024).toFixed(1)} KB)`)
  console.log(`📄 File Manifest : ${MANIFEST_FILE}`)
  console.log(`🔑 SHA-256       : ${checksum}`)
  console.log('--------------------------------------------------')
  console.log('Langkah rilis: Upload kedua file di atas ke server:')
  console.log(`  -> ${manifest.url}`)
  console.log(`  -> https://swb-rc.wahana.com/ota/manifest.json\n`)
}

// ============================================================================
// COMMAND: ROLLBACK
// ============================================================================
function runRollback() {
  const manifest = loadManifest()
  const currentBuild = manifest.build
  const toBuild = parseInt(getArgValue('--to', currentBuild - 1), 10)

  if (toBuild <= 0) {
    console.error('❌ Error: Nomor build rollback harus lebih besar dari 0.')
    process.exit(1)
  }

  console.log(`\n⚠️  EMERGENCY ROLLBACK DIAKTIFKAN!`)
  console.log(`Mengembalikan Build dari ${currentBuild} ke ${toBuild}...`)

  manifest.build = toBuild
  saveManifest(manifest)

  console.log('✅ Rollback berhasil dicatat di manifest.json.')
  console.log(`Semua pengguna yang membuka aplikasi akan diarahkan ke Build ${toBuild}.\n`)
}

// ============================================================================
// COMMAND: FEATURE MANAGEMENT
// ============================================================================
function handleFeatureCommand() {
  let subCmd = ''
  let featKey = ''

  if (args[0] && args[0].includes(':')) {
    subCmd = args[0].split(':')[1]
    featKey = args[1]
  } else {
    subCmd = args[1]
    featKey = args[2]
  }

  const manifest = loadManifest()
  if (!manifest.features) manifest.features = {}

  if (subCmd === 'list' || !subCmd) {
    showStatus()
    return
  }

  if (!featKey) {
    console.error('❌ Error: Masukkan nama kunci fitur. Contoh: feature:enable payment.qris')
    process.exit(1)
  }

  if (!manifest.features[featKey]) {
    manifest.features[featKey] = {
      enabled: true,
      description: '',
      users: [],
      branches: [],
      releaseDate: null,
      percentage: 100
    }
  }

  let feat = manifest.features[featKey]
  if (typeof feat === 'boolean') {
    feat = { enabled: feat, users: [], branches: [], releaseDate: null, percentage: 100 }
    manifest.features[featKey] = feat
  }

  if (subCmd === 'enable') {
    feat.enabled = true
    if (hasArg('--all')) {
      feat.users = []
      feat.branches = []
      feat.releaseDate = null
      feat.percentage = 100
      console.log(`✅ Fitur "${featKey}" sekarang DIAKTIFKAN UNTUK 100% PENGGUNA!`)
    } else {
      console.log(`✅ Fitur "${featKey}" sekarang DIAKTIFKAN.`)
    }
  } else if (subCmd === 'disable') {
    feat.enabled = false
    console.log(`🛑 KILL-SWITCH DIAKTIFKAN! Fitur "${featKey}" sekarang DINONAKTIFKAN untuk semua pengguna.`)
  } else if (subCmd === 'set') {
    if (hasArg('--enable')) feat.enabled = true
    if (hasArg('--disable')) feat.enabled = false
    if (hasArg('--all')) {
      feat.users = []
      feat.branches = []
      feat.releaseDate = null
      feat.percentage = 100
    }

    const descArg = getArgValue('--desc')
    if (descArg) feat.description = descArg

    const usersArg = getArgValue('--users')
    if (usersArg !== null) {
      feat.users = usersArg.split(',').map((s) => s.trim()).filter(Boolean)
    }

    const branchArg = getArgValue('--branch')
    if (branchArg !== null) {
      feat.branches = branchArg.split(',').map((s) => s.trim()).filter(Boolean)
    }

    const dateArg = getArgValue('--date')
    if (dateArg !== null) {
      feat.releaseDate = dateArg === 'none' || dateArg === 'null' ? null : dateArg
    }

    const percentArg = getArgValue('--percent')
    if (percentArg !== null) {
      feat.percentage = parseInt(percentArg, 10)
    }

    console.log(`✅ Pengaturan fitur "${featKey}" berhasil diperbarui.`)
  }

  saveManifest(manifest)
  showStatus()
}

// ============================================================================
// MODE INTERAKTIF (WIZARD TANYA-JAWAB)
// ============================================================================
function runInteractive() {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
  })

  function ask(question) {
    return new Promise((resolve) => rl.question(question, resolve))
  }

  async function startWizard() {
    console.log('\n====================================================')
    console.log('   ⚙️  SWB OTA & FEATURE MANAGEMENT WIZARD')
    console.log('====================================================')
    console.log('1. Cek Status Rilis & Fitur')
    console.log('2. Build & Kemas Bundle OTA Baru')
    console.log('3. Nyalakan / Matikan Fitur (Kill-Switch)')
    console.log('4. Atur Target Pengguna Fitur (User ID / Cabang / Tanggal)')
    console.log('5. Emergency Rollback')
    console.log('0. Keluar')
    console.log('----------------------------------------------------')

    const choice = (await ask('Pilih menu [0-5]: ')).trim()

    if (choice === '1') {
      showStatus()
      rl.close()
    } else if (choice === '2') {
      const manifest = loadManifest()
      const nextBuild = (await ask(`Masukkan nomor build baru [Default: ${manifest.build + 1}]: `)).trim()
      if (nextBuild) {
        process.argv.push('--build', nextBuild)
      }
      rl.close()
      runBuild()
    } else if (choice === '3') {
      const manifest = loadManifest()
      const keys = Object.keys(manifest.features || {})
      console.log('\nDaftar fitur yang ada:')
      keys.forEach((k, idx) => console.log(`  ${idx + 1}. ${k}`))
      const featName = (await ask('\nMasukkan kunci fitur (contoh: payment.qris): ')).trim()
      const action = (await ask('Pilih aksi (1: Nyalakan untuk semua, 2: Matikan/Kill-switch): ')).trim()

      if (action === '1') {
        process.argv = ['node', 'cli', 'feature', 'enable', featName, '--all']
      } else {
        process.argv = ['node', 'cli', 'feature', 'disable', featName]
      }
      rl.close()
      handleFeatureCommand()
    } else if (choice === '4') {
      const featName = (await ask('\nMasukkan nama fitur (contoh: payment.qris): ')).trim()
      console.log('Masukkan target pengguna (kosongkan jika tidak ingin dibatasi):')
      const users = (await ask('Whitelist User ID (pisahkan koma, misal: 101,102): ')).trim()
      const branches = (await ask('Whitelist Cabang (pisahkan koma, misal: HUB-JKT): ')).trim()
      const date = (await ask('Jadwalkan rilis tanggal (format: YYYY-MM-DD HH:mm atau kosongkan): ')).trim()
      const percent = (await ask('Persentase Rollout (misal: 20 atau kosongkan untuk 100%): ')).trim()

      process.argv = ['node', 'cli', 'feature', 'set', featName]
      if (users) process.argv.push('--users', users)
      if (branches) process.argv.push('--branch', branches)
      if (date) process.argv.push('--date', date)
      if (percent) process.argv.push('--percent', percent)

      rl.close()
      handleFeatureCommand()
    } else if (choice === '5') {
      const confirm = (await ask('⚠️ Apakah Anda yakin ingin melakukan Rollback? (y/n): ')).trim().toLowerCase()
      if (confirm === 'y') {
        rl.close()
        runRollback()
      } else {
        console.log('Rollback dibatalkan.')
        rl.close()
      }
    } else {
      console.log('Sampai jumpa!')
      rl.close()
    }
  }

  startWizard()
}

// Router Perintah
if (command === 'status') {
  showStatus()
} else if (command === 'build') {
  runBuild()
} else if (command === 'rollback') {
  runRollback()
} else if (command.startsWith('feature')) {
  handleFeatureCommand()
} else {
  runInteractive()
}
