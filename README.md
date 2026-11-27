# Ingres Parts

Aplikasi pengaturan bawaan untuk **POCO F4 GT / Redmi K50 Gaming (ingres)** di ROM berbasis AOSP
(LineageOS dan turunannya, AOSPA, dll), Android 16 dan 17. Muncul sebagai entri **Ingres Parts**
di halaman utama Setelan.

Repo ini juga berisi **CPU/GPU per Aplikasi** (IngresPerf), dulu repo `CPU_GPU_perAPP_ingres`.
Sekarang menjadi bagian dari Ingres Parts dan dibuka dari menu **Fitur lain**.

Dibuat oleh [kingD2N](https://github.com/kingD2N). Diuji di Evolution X (Android 17).

## Fitur

| Fitur | Node | Keterangan |
|---|---|---|
| Info baterai: kesehatan, kapasitas, siklus, suhu, tegangan, arus, daya, charger | `qcom-battery/fg1_soh`, `fg1_cycle`, `real_type`, `power_max`, `power_supply/battery` | diperbarui tiap 2 detik selama halaman terbuka; arah arus dipelajari saat charger dicabut |
| Batas cas: Normal / Optimal 80% / Batas 50–95% | `qcom-battery/night_charging`, `input_suspend` | batas kustom lanjut cas lagi 5% di bawah batas |
| Bypass charging (eksperimen) | `power_supply/battery/constant_charge_current` | HP memakai daya charger tanpa mengisi baterai; bisa hanya saat main game |
| Cas dingin | `power_supply/battery/constant_charge_current` | arus cas dibatasi 3 A saat baterai mencapai batas suhu (36–45°C, bawaan 40), 1,5 A di batas+4°C; dilepas (`-1`) di batas−2°C atau saat charger dicabut |
| Profil thermal: Hemat / Harian / Seimbang / Gaming / Performa | `thermal_message/sconfig` (11 / 0 / 20 / 13 / 10) | Seimbang = bawaan ROM (20), tidak ditulis bila tidak diubah |
| Mode game: daftar game, thermal khusus game, sentuhan 480 Hz, overlay FPS & suhu | `touch_dev/bump_sample_rate`, `sde-crtc-0/measured_fps` | aktif saat game di daftar terbuka di layar depan |
| Sentuhan 480 Hz di semua aplikasi | `touch_dev/bump_sample_rate` | pengganti "High touch polling rate" di Setelan |
| Mode warna Flat | `disp-DSI-0/disp_param` = `6 1` | ditulis ulang saat layar menyala (panel ingres tidak mendukung DC dimming) |
| Pembersih speaker | speaker bawaan | nada 165 Hz 30 detik, volume dikembalikan sesudahnya |
| CPU/GPU per Aplikasi | `cpufreq/policy0,4,7`, `kgsl-3d0/devfreq` | profil frekuensi CPU/GPU dan governor per aplikasi (lihat di bawah) |
| Fitur lain | LED, Dolby | pintasan, hanya yang terpasang |

Bahasa: Indonesia dan Inggris (mengikuti bahasa HP).

## Pasang

1. Tambahkan ke local manifest:

   ```xml
   <project path="packages/apps/IngresParts" name="kingD2N/Ingres_Parts" remote="github" revision="main" />
   ```

   (`remote="github"` = `https://github.com`; sesuaikan dengan nama remote di manifest kamu.)

2. `device.mk` (`INGRES_PARTS` diset **sebelum** inherit sm8450-common, lihat langkah 4):

   ```make
   INGRES_PARTS := true
   $(call inherit-product, device/xiaomi/sm8450-common/common.mk)
   ...
   $(call inherit-product, packages/apps/IngresParts/ingres_parts.mk)
   ```

3. `BoardConfig.mk` (paling bawah):

   ```make
   -include packages/apps/IngresParts/BoardConfigParts.mk
   ```

4. Disarankan, di `device/xiaomi/sm8450-common/common.mk` (LineageOS): sakelar "High touch polling rate"
   di Setelan dihilangkan, supaya sentuhan hanya diatur dari Ingres Parts dan tidak saling menimpa.

   ```make
   ifneq ($(INGRES_PARTS),true)
   $(call soong_config_set, XIAOMI_TOUCH, HIGH_TOUCH_POLLING_PATH, /sys/devices/virtual/touch/touch_dev/bump_sample_rate)
   endif
   ```

5. Build ROM seperti biasa.

Sakelar, diisi di `device.mk` **dan** `BoardConfig.mk` sebelum include:

| Sakelar | Bawaan | Arti |
|---|---|---|
| `INGRES_PARTS_APP` | `true` | aplikasi Ingres Parts; `false` = hanya CPU/GPU per Aplikasi |
| `INGRES_PARTS_PERF` | `true` | CPU/GPU per Aplikasi |
| `INGRES_PARTS_PATH` | `packages/apps/IngresParts` | lokasi repo ini di source |

### Pembaruan dari CPU_GPU_perAPP_ingres

- Path di manifest jadi `packages/apps/IngresParts` (repo lama otomatis diarahkan GitHub ke nama baru).
- `ingres_perf.mk` → `ingres_parts.mk`, `BoardConfigPerf.mk` → `BoardConfigParts.mk`, `INGRES_PERF_PATH` → `INGRES_PARTS_PATH`.
- Profil CPU/GPU yang sudah tersimpan di HP tetap terpakai (nama paket `org.ingres.perf` tidak berubah).
- Entri Setelan > Sistem > CPU/GPU per Aplikasi disembunyikan selama Ingres Parts terpasang; bukanya dari Ingres Parts > Fitur lain.

## Cara kerja

- App `IngresParts` (`org.ingres.parts`) dan `IngresPerf` (`org.ingres.perf`) bersertifikat platform,
  di `system_ext/priv-app`. Keduanya tidak menulis sysfs sendiri: app mengisi properti
  `sys.ingres.parts.*` / `sys.ingres.perf.*`, lalu `init` (`init/*.rc`) yang menulis node.
- Sampling sentuh (driver Goodix Berlin): `bump_sample_rate` 1 = 480 Hz, 0 = 240 Hz. Node ini tidak
  bisa dibaca, jadi nilainya ditentukan penuh dari setelan Ingres Parts.
- Bypass: arus cas (FCC) diisi 0. Saat selesai, FCC dilepas dengan `-1`. Driver `qti_battery_charger`
  meneruskannya ke ADSP sebagai `UINT_MAX` (tanpa batas), jadi pengisian berikutnya tidak terbatasi.
  Menulis angka arus biasa justru menjadi batas tetap sampai reboot.
- Mode game memakai aplikasi yang **difokuskan**: jendela mengambang atau split screen di atas game
  dianggap keluar dari game.

### CPU/GPU per Aplikasi

- Profil per aplikasi: frekuensi min/max tiap cluster CPU (little/big/prime), governor CPU, dan min/max GPU.
  Profil **Default** diterapkan ~20 detik setelah boot dan saat aplikasi di depan tidak punya profil.
- Kolom bisa dibiarkan **Tidak diatur** (mengikuti sistem). Nilai sebelumnya dipulihkan saat keluar dari aplikasi berprofil.
- `init.ingres_perf.rc` menulis dengan urutan aman (kernel menolak min > max): governor, lantai min, max, min.
- Node: CPU `cpufreq/policy0` (little), `policy4` (big), `policy7` (prime), GPU `/sys/class/kgsl/kgsl-3d0/devfreq/`.
  Cocok untuk SM8450/SM8475; untuk SoC lain sesuaikan `perf/app/java/org/ingres/perf/logic/PerfNodes.java`
  dan `init/init.ingres_perf.rc`.

## SELinux

`BoardConfigParts.mk` menyesuaikan diri dengan source ROM:

| Folder | Dipasang bila | Isi |
|---|---|---|
| `sepolicy/parts/private` | selalu | properti `sys.ingres.parts.*`, init menulis `sysfs_thermal` |
| `sepolicy/parts/private-a17`, `sepolicy/perf/private-a17` | Android 17 | properti untuk `platform_app_36` |
| `sepolicy/parts/vendor` (+`-a17`) | tipe `vendor_sysfs_battery_supply` & `vendor_sysfs_graphics` ada | baterai, overlay FPS, `disp_param` di AOSPA |
| `sepolicy/parts/vendor-touch` | tipe `vendor_sysfs_touch` ada | sentuhan 480/240 Hz |
| `sepolicy/parts/vendor-touch-genfs` | `bump_sample_rate` belum diberi label di `device/xiaomi` (mis. AOSPA) | label `vendor_sysfs_touch` |
| `sepolicy/parts/vendor-displayfeature` | tipe `vendor_sysfs_displayfeature` ada (LineageOS) | mode Flat |
| `sepolicy/perf/private` | CPU/GPU per Aplikasi aktif | properti `sys.ingres.perf.*`, init menulis cpufreq |
| `sepolicy/perf/vendor` (+`-a17`) | tipe `vendor_sysfs_kgsl` ada | GPU |

Android 17 dikenali dari `platform_app_36` di `system/sepolicy`. Bila sebuah tipe tidak ada, build tetap
jalan dan muncul peringatan `IngresParts: ...` di log.

## Integrasi dengan aplikasi lain (opsional)

Menu **Fitur lain** membuka aplikasi ini bila terpasang:

- LED: `org.lineageos.leds/.MainActivity`
- CPU/GPU per Aplikasi: `org.ingres.perf/.ui.PerfActivity` (repo ini)
- Dolby: `org.lunaris.dolby/.ui.DolbyActivity`

Bila aplikasi itu memindahkan ikon peluncur / entri Setelannya ke activity-alias bernama
`LauncherEntry` / `SettingsEntry` (lihat `parts/app/kotlin/org/ingres/parts/EntryPoints.kt`), Ingres Parts
menyembunyikan entri ganda itu supaya semua fitur ada di satu menu. IngresPerf di repo ini sudah memakai alias.

## Hindari bentrok

- Jangan dipasang bersamaan dengan paket `vendor/ingres/addons` yang `INGRES_ADDONS_PARTS` / `INGRES_ADDONS_PERF`-nya menyala; isinya sama.
- Modul KernelSU/Magisk yang menulis `sconfig` (mis. DThermal) menimpa profil thermal; yang terus menulis `scaling_max_freq` menimpa profil CPU. Matikan salah satunya.
- AOSPA: XiaomiParts punya "profil thermal per aplikasi" (mati bawaan). Biarkan mati.
- Bila langkah 4 dilewati, sakelar "High touch polling rate" di Setelan ikut mengatur node sentuhan yang sama.

## Cek di HP (terminal root)

```sh
getprop | grep -e ingres.parts -e ingres.perf
cat /sys/devices/virtual/thermal/thermal_message/sconfig
cat /sys/class/qcom-battery/night_charging /sys/class/qcom-battery/input_suspend
dmesg | grep "report rate switch" | tail -2      # 480HZ / 240HZ
dmesg | grep avc | grep -e ingres -e init | tail
```

## Tes logika (di PC)

```sh
javac -d /tmp/parts $(find parts/app/java parts/app/test -name "*.java") && java -cp /tmp/parts org.ingres.parts.logic.HostTests
javac -d /tmp/perf $(find perf/app/java perf/app/test -name "*.java") && java -cp /tmp/perf org.ingres.perf.logic.HostTests
```

## Lisensi

Apache-2.0
