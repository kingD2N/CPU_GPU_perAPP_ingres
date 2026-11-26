# CPU_GPU_perAPP_ingres

Pengaturan **CPU/GPU per Aplikasi** untuk ROM Android 16 dan 17. Setiap aplikasi bisa punya profil sendiri: frekuensi minimum dan maksimum tiap cluster CPU, governor CPU, dan frekuensi GPU. Profil diterapkan otomatis saat aplikasi itu dibuka.

Dibuat oleh **kingD2N** untuk POCO F4 GT (ingres, Snapdragon 8 Gen 1).

## Fitur

- Menu **Setelan → Sistem → CPU/GPU per Aplikasi**, tanpa ikon di peluncur.
- Profil **Default**, diterapkan sekitar 20 detik setelah boot dan saat aplikasi yang dibuka tidak punya profil sendiri.
- Setiap kolom bisa dibiarkan **Tidak diatur**, artinya nilainya mengikuti sistem (modul atau Kernel Manager) dan tidak ditimpa.
- Saat keluar dari aplikasi berprofil, nilai sistem sebelumnya dipulihkan.
- Governor GPU dan thermal tidak disentuh.

## Cara kerja

App `IngresPerf` tidak menulis sysfs sendiri. App mengisi properti `sys.ingres.perf.*` lalu menaikkan `sys.ingres.perf.apply`. File `init.ingres_perf.rc` yang kemudian menulis node sysfs dengan urutan yang aman, karena kernel menolak min > max:

1. Governor CPU
2. Batas bawah (floor) min
3. Max target
4. Min target

## Syarat perangkat

Node yang dipakai (lihat `app/java/org/ingres/perf/logic/PerfNodes.java` dan `init/init.ingres_perf.rc`):

| Bagian | Node |
|---|---|
| CPU little | `/sys/devices/system/cpu/cpufreq/policy0/` |
| CPU big | `/sys/devices/system/cpu/cpufreq/policy4/` |
| CPU prime | `/sys/devices/system/cpu/cpufreq/policy7/` |
| GPU (Adreno) | `/sys/class/kgsl/kgsl-3d0/devfreq/` |

Susunan cluster 0/4/7 cocok untuk SM8450 (8 Gen 1) dan SM8475 (8+ Gen 1). Untuk SoC lain, sesuaikan nomor `policy` di kedua file itu.

## Cara pakai

### 1. Local manifest

```xml
<project path="packages/apps/IngresPerf" name="kingD2N/CPU_GPU_perAPP_ingres" remote="github" revision="main" />
```

Ganti `remote` dengan nama remote GitHub di manifest ROM-mu (misal `github` atau `gh`).

### 2. `device.mk`

```make
$(call inherit-product, packages/apps/IngresPerf/ingres_perf.mk)
```

### 3. `BoardConfig.mk` (paling bawah)

```make
-include packages/apps/IngresPerf/BoardConfigPerf.mk
```

Sepolicy dipasang otomatis sesuai ROM:

- **Android 17** dikenali dari `platform_app_36` di `system/sepolicy`. App platform dengan target SDK < 37 berjalan sebagai `platform_app_36`, jadi aturan tambahan di `sepolicy/*-a17` ikut dipasang.
- **Aturan GPU** (`sepolicy/vendor`) hanya dipasang bila tipe `vendor_sysfs_kgsl` ada di source device/hardware. Aturan dengan tipe yang tidak ada akan merusak build sepolicy vendor.

Kalau path-nya bukan `packages/apps/IngresPerf`, isi `INGRES_PERF_PATH := <path>` sebelum kedua baris di atas.

## Catatan

- App ditandatangani `platform` dan dipasang di `system_ext` sebagai priv-app.
- Jangan dipasang bersamaan dengan paket `vendor/ingres/addons` yang `INGRES_ADDONS_PERF`-nya menyala, karena isinya sama. Matikan salah satunya.
- Modul KSU yang terus-menerus menulis `scaling_max_freq` akan menimpa profil dari app ini.

## Tes logika

Logika profil (`app/java`) tidak bergantung pada Android dan bisa dites di PC:

```bash
mkdir -p /tmp/perf-test
javac -d /tmp/perf-test $(find app/java app/test -name "*.java")
java -cp /tmp/perf-test org.ingres.perf.logic.HostTests
```

## Lisensi

Apache-2.0
