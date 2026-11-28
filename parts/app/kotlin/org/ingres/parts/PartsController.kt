/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.content.Context
import android.util.Log
import org.ingres.parts.logic.ChargeCurrent
import org.ingres.parts.logic.ChargePolicy
import org.ingres.parts.logic.ColorModes
import org.ingres.parts.logic.CoolCharge
import org.ingres.parts.logic.ThermalProfile
import org.ingres.parts.logic.TriggerAction

/**
 * Otak Ingres Parts. Semua method dipanggil di thread utama.
 *
 * Masukan: status baterai, aplikasi di depan, layar, boot, dan setelan dari UI.
 * Keluaran: properti sys.ingres.parts.* (ditulis ke sysfs oleh init) dan overlay FPS.
 * Properti hanya ditulis bila nilainya berubah dari yang terakhir ditulis.
 */
class PartsController(private val ctx: Context) {
    val prefs = PartsPrefs(ctx)
    private val overlay = FpsOverlay(ctx)
    /** Info baterai untuk halaman Ingres Parts (dibaca di luar thread utama). */
    val battery = BatteryReader(ctx, prefs)
    /** Riwayat baterai (dibaca halaman Riwayat dan laporan bug). */
    val history = BatteryHistory(ctx, prefs, battery)
    private val overrides = GameOverrides(ctx, prefs)
    private val toolbox = GameToolbox(ctx, this)

    /** Paket di layar depan terakhir (untuk Bersihkan RAM). */
    var topPackage: String? = null
        private set

    // Masukan
    var batteryLevel = -1
        private set
    var plugged = false
        private set
    /** Suhu baterai (derajat C), NaN = belum diketahui. */
    var batteryTemp = Float.NaN
        private set
    /** Tingkat cas dingin yang berlaku (CoolCharge.NORMAL/HOT/VERY_HOT). */
    var coolLevel = CoolCharge.NORMAL
        private set
    var gameActive = false
        private set
    private var booted = false

    // Keluaran terakhir. init.ingres_parts.rc mengembalikan input_suspend dan
    // night_charging ke 0 setiap boot, jadi nilai awalnya "0".
    private val written =
        mutableMapOf<String, String>(
            SysNodes.PROP_INPUT_SUSPEND to "0",
            SysNodes.PROP_NIGHT_CHARGING to "0",
        )
    var charge: ChargePolicy.State = ChargePolicy.decide(ChargePolicy.MODE_NORMAL, 0, -1, false, false, false)
        private set

    private var flatNonce = 0

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val gameKeysToken = Any()

    /** Dipanggil sekali saat proses mulai. */
    fun start() {
        // Batas arus yang belum dilepas (app atau HP mati saat bypass/cas dingin): lepas dulu.
        if (prefs.pendingFccRestore > 0) {
            Log.w(TAG, "Melepas batas arus cas yang tertinggal")
            if (write(SysNodes.PROP_FCC, SysNodes.FCC_RELEASE, force = true)) prefs.pendingFccRestore = 0
        }
        applyAll()
    }

    fun onBootCompleted() {
        booted = true
        applyAll()
        // HAL layar bisa menyetel ulang mode warna saat boot; tulis ulang Flat sekali lagi.
        if (prefs.flatMode) pushFlat("1")
    }

    /** @param tempC suhu baterai (derajat C), NaN bila tidak ada */
    fun onBattery(level: Int, isPlugged: Boolean, tempC: Float) {
        battery.learnIfNeeded(isPlugged)
        history.onBattery(level, isPlugged, tempC)
        val sameTemp = tempC == batteryTemp || (tempC.isNaN() && batteryTemp.isNaN())
        if (level == batteryLevel && isPlugged == plugged && sameTemp) return
        batteryLevel = level
        plugged = isPlugged
        batteryTemp = tempC
        applyCharge()
    }

    fun onForeground(pkg: String) {
        topPackage = pkg
        setGameActive(pkg in prefs.games)
    }

    fun onScreenOff() {
        history.onScreen(false)
        setGameActive(false)
    }

    fun onScreenOn() {
        history.onScreen(true)
        // Panel bisa melupakan mode Flat saat layar mati; tulis ulang.
        if (prefs.flatMode) pushFlat("1")
    }

    private fun setGameActive(active: Boolean) {
        if (active == gameActive) return
        gameActive = active
        Log.d(TAG, "Mode game: $active")
        applyAll()
    }

    /** Panggil setelah setelan berubah dari UI. */
    fun applyAll() {
        applyCharge()
        applyThermal()
        applyTouch()
        applyOverlay()
        applyFlat()
        applyGameKeys()
        applyGameExtras()
        applyAccessibility()
    }

    /**
     * Selama game: notifikasi pop-up, kunci kecerahan, mode warna, dan Game Toolbox. Di luar game
     * (juga saat app baru mulai) semua dikembalikan ke nilai aslinya.
     */
    private fun applyGameExtras() {
        overrides.headsUp(gameActive && prefs.gameHideHeadsUp)
        overrides.brightness(gameActive && prefs.gameLockBrightness)
        overrides.colorMode(if (gameActive) prefs.gameColorMode else ColorModes.FOLLOW)
        if (gameActive && prefs.toolboxEnabled) toolbox.show() else toolbox.hide()
    }

    /** Ada aksi tombol bahu di luar game. */
    val triggersOutsideGame: Boolean
        get() =
            prefs.triggerUpper != TriggerAction.NONE ||
                prefs.triggerLower != TriggerAction.NONE ||
                prefs.triggerCombo != TriggerAction.NONE

    /** Layanan aksesibilitas hanya menyala bila tombol bahu di luar game atau Game Toolbox dipakai. */
    private fun applyAccessibility() {
        IngresAccessibilityService.update(ctx, prefs.toolboxEnabled || triggersOutsideGame)
        IngresAccessibilityService.instance?.setKeyFilter(triggersOutsideGame)
    }

    private fun applyCharge() {
        val bypassWanted = prefs.bypassEnabled && (!prefs.bypassGameOnly || gameActive)
        val next =
            ChargePolicy.decide(
                prefs.chargeMode,
                prefs.chargeLimit,
                batteryLevel,
                plugged,
                charge.suspendInput,
                bypassWanted,
            )
        charge = next
        coolLevel = CoolCharge.decide(prefs.coolEnabled, plugged, batteryTemp, prefs.coolTemp, coolLevel)
        write(SysNodes.PROP_NIGHT_CHARGING, if (next.nightCharging) "1" else "0")
        // Bypass (FCC 0) menang; selain itu batas paling ketat dari cas dingin dan batas daya.
        val fcc =
            ChargeCurrent.target(
                plugged,
                next.bypass,
                CoolCharge.fccUa(coolLevel),
                ChargeCurrent.powerCapUa(prefs.powerLimitEnabled, prefs.powerLimitWatts),
            )
        applyFcc(fcc)
        write(SysNodes.PROP_INPUT_SUSPEND, if (next.suspendInput) "1" else "0")
    }

    /**
     * Batas arus cas baterai (FCC): "0" = bypass (HP berjalan dari charger), angka uA = cas dingin
     * atau batas daya, null = tanpa batas. ingres memakai qti_battery_charger yang meneruskan nilai ke ADSP sebagai
     * batas tetap, jadi saat selesai FCC TIDAK diisi angka (itu membatasi cas berikutnya),
     * melainkan dilepas dengan nilai negatif (UINT_MAX di ADSP).
     */
    private fun applyFcc(target: String?) {
        val pending = prefs.pendingFccRestore > 0
        if (target != null) {
            // Tandai dulu (commit) supaya batas bisa dilepas walau proses mati sesudahnya.
            if (!pending) prefs.pendingFccRestore = 1
            write(SysNodes.PROP_FCC, target, force = !pending)
        } else if (pending) {
            if (write(SysNodes.PROP_FCC, SysNodes.FCC_RELEASE, force = true)) prefs.pendingFccRestore = 0
        }
    }

    private fun applyThermal() {
        if (!booted) return
        val p = ThermalProfile.effective(prefs.thermalBase, gameActive, prefs.thermalGame)
        val value = p.sconfig.toString()
        // Profil bawaan ROM (20) sudah ditulis ROM saat boot: jangan menulis apa pun sampai
        // pengguna memilih profil lain, supaya bawaan Ingres Parts = perilaku ROM.
        if (written[SysNodes.PROP_SCONFIG] == null && p.sconfig == ThermalProfile.ROM_DEFAULT_SCONFIG) return
        write(SysNodes.PROP_SCONFIG, value)
    }

    /**
     * Sampling sentuh (driver Goodix ingres): bump_sample_rate 1 = 480 Hz, 0 = 240 Hz. Node tidak
     * bisa dibaca, jadi nilainya ditentukan penuh dari setelan Ingres Parts: 480 Hz di semua aplikasi,
     * atau hanya saat game di daftar dibuka. Sakelar "High touch polling rate" di Setelan
     * dihilangkan (sm8450-common) selama Ingres Parts terpasang, supaya tidak bentrok.
     */
    private fun applyTouch() {
        val high = prefs.touchAlways || (gameActive && prefs.gameTouch)
        write(SysNodes.PROP_TOUCH_BOOST, if (high) "1" else "0")
    }

    private fun applyOverlay() {
        if (gameActive && prefs.gameOverlay) overlay.show() else overlay.hide()
    }

    private fun applyFlat() {
        val v = if (prefs.flatMode) "1" else "0"
        // Belum pernah diubah dan mati: biarkan panel apa adanya.
        if (written[SysNodes.PROP_FLAT] == null && v == "0") return
        if (written[SysNodes.PROP_FLAT] != v) pushFlat(v)
    }

    /** Isi nilai lalu naikkan nonce pemicu, supaya init menulis walau nilainya sama. */
    private fun pushFlat(v: String) {
        if (write(SysNodes.PROP_FLAT, v, force = true)) {
            flatNonce++
            SysNodes.setProp(SysNodes.PROP_FLAT_APPLY, "${android.os.SystemClock.elapsedRealtime()}.$flatNonce")
        }
    }

    /**
     * Tombol bahu: gamekeyd (GameKeys) dijalankan ROM saat boot selesai dan mengambil alih layar
     * sentuh (sentuhan diteruskan lewat uinput). Dimatikan = layanan aksesibilitas GameKeys dimatikan
     * dan prosesnya dihentikan, lalu gamekeyd dihentikan. Dinyalakan = gamekeyd dijalankan, layanan
     * aksesibilitas menyusul setelah gamekeyd siap (lihat GameKeysControl). Tidak menyentuh apa pun
     * selama pengguna belum pernah mematikannya, dan baru ditulis sesudah boot.
     */
    private fun applyGameKeys() {
        if (!booted) return
        val on = prefs.gameKeysEnabled
        val v = if (on) "1" else "0"
        if (written[SysNodes.PROP_GAMEKEYS] == null && on) return
        if (written[SysNodes.PROP_GAMEKEYS] == v) return
        if (!EntryPoints.isInstalled(ctx, GameKeysControl.PKG)) return
        handler.removeCallbacksAndMessages(gameKeysToken)
        if (on) {
            write(SysNodes.PROP_GAMEKEYS, v)
            handler.postDelayed({ GameKeysControl.setAccessibility(ctx, true) }, gameKeysToken, GAMEKEYS_START_MS)
        } else {
            GameKeysControl.setAccessibility(ctx, false)
            GameKeysControl.forceStop(ctx)
            write(SysNodes.PROP_GAMEKEYS, v)
        }
    }

    /** Uji overlay dari UI tanpa harus membuka game. */
    fun previewOverlay(show: Boolean) {
        if (show) overlay.show() else if (!(gameActive && prefs.gameOverlay)) overlay.hide()
    }

    private fun write(key: String, value: String, force: Boolean = false): Boolean {
        if (!force && written[key] == value) return true
        val ok = SysNodes.setProp(key, value)
        if (ok) written[key] = value
        return ok
    }

    private companion object {
        const val TAG = "IngresParts"
        /** Jeda sebelum layanan aksesibilitas GameKeys dinyalakan lagi (gamekeyd mendaftarkan diri). */
        const val GAMEKEYS_START_MS = 1500L
    }
}
