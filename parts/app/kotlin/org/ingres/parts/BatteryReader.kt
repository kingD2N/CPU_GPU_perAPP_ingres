/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import org.ingres.parts.logic.BatteryInfo

/**
 * Pembaca info baterai untuk halaman Ingres Parts. Sumber: siaran baterai Android, BatteryManager,
 * dan node power_supply/battery (path asli di /sys/devices) + qcom-battery (dibaca langsung, aturan SELinux di
 * sepolicy/parts/vendor). Node qcom-battery dibaca lewat ADSP, jadi panggil [read] di luar
 * thread utama.
 */
class BatteryReader(private val ctx: Context, private val prefs: PartsPrefs) {
    private val bm: BatteryManager? = ctx.getSystemService(BatteryManager::class.java)

    /** Folder power_supply/battery yang ada di HP ini, dicari sekali. */
    private val psy: String by lazy {
        PSY_CANDIDATES.firstOrNull { java.io.File(it).isDirectory } ?: PSY_CANDIDATES[0]
    }

    // Nilai yang jarang berubah, dibaca ulang tiap SLOW_MS.
    private var slowAt = 0L
    private var health = BatteryInfo.UNKNOWN
    private var fullMah = BatteryInfo.UNKNOWN
    private var designMah = BatteryInfo.UNKNOWN
    private var psyCycles = BatteryInfo.UNKNOWN

    class Snapshot(
        val level: Int,
        /** Persen kesehatan, BatteryInfo.UNKNOWN bila tidak ada. */
        val healthPercent: Int,
        /** BatteryManager.BATTERY_HEALTH_*. */
        val healthCode: Int,
        val fullMah: Int,
        val designMah: Int,
        val cycles: Int,
        val tempC: Float,
        val voltageMv: Int,
        /** Positif = mengisi, negatif = terpakai. */
        val currentMa: Int,
        val watts: Float,
        val plugged: Boolean,
        val chargerKind: Int,
        val chargerWatts: Int,
        val minutesToFull: Int,
    )

    @Synchronized
    fun read(): Snapshot? {
        val i: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val now = SystemClock.elapsedRealtime()
        val voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, BatteryInfo.UNKNOWN)
        if (slowAt == 0L || now - slowAt > SLOW_MS) {
            slowAt = now
            val full = BatteryInfo.parseInt(SysNodes.read(psy + PSY_FULL))
            val design = BatteryInfo.parseInt(SysNodes.read(psy + PSY_DESIGN))
            fullMah = BatteryInfo.fullMah(full, design, voltage)
            designMah = BatteryInfo.toMah(design)
            health = BatteryInfo.healthPercent(full, design, voltage)
            psyCycles = BatteryInfo.parseInt(SysNodes.read(psy + PSY_CYCLE))
        }

        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val tempC = BatteryInfo.tempC(i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, BatteryInfo.UNKNOWN))
        val cycles = BatteryInfo.cycles(i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, BatteryInfo.UNKNOWN), psyCycles)

        val currentUa = currentUa()
        learnSign(currentUa, plugged)
        val currentMa = BatteryInfo.signedMa(currentUa, prefs.dischargeSign)

        val kind =
            BatteryInfo.chargerKind(plugged, if (plugged) SysNodes.read(NODE_REAL_TYPE) else null)
        val chargerW =
            if (plugged) BatteryInfo.chargerWatts(true, BatteryInfo.parseInt(SysNodes.read(NODE_POWER_MAX)))
            else BatteryInfo.UNKNOWN
        val minutes =
            if (plugged) {
                try {
                    BatteryInfo.minutesLeft(bm?.computeChargeTimeRemaining() ?: -1L)
                } catch (e: Exception) {
                    BatteryInfo.UNKNOWN
                }
            } else {
                BatteryInfo.UNKNOWN
            }

        return Snapshot(
            level = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            healthPercent = health,
            healthCode = i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN),
            fullMah = fullMah,
            designMah = designMah,
            cycles = cycles,
            tempC = tempC,
            voltageMv = voltage,
            currentMa = currentMa,
            watts = BatteryInfo.watts(voltage, currentMa),
            plugged = plugged,
            chargerKind = kind,
            chargerWatts = chargerW,
            minutesToFull = minutes,
        )
    }

    /**
     * Dipanggil saat siaran baterai datang (thread utama) selama tanda arus belum diketahui:
     * charger tercabut = baterai pasti terpakai. Tanpa kunci: hanya menyentuh setelan.
     */
    fun learnIfNeeded(plugged: Boolean) {
        if (plugged || prefs.dischargeSign != 0) return
        learnSign(currentUa(), false)
    }

    private fun learnSign(currentUa: Int, plugged: Boolean) {
        val sign = BatteryInfo.learnDischargeSign(currentUa, plugged)
        if (sign != 0 && sign != prefs.dischargeSign) prefs.dischargeSign = sign
    }

    /** Arus baterai sekarang (mA, positif = mengisi), BatteryInfo.UNKNOWN bila tidak terbaca. */
    fun currentMa(): Int = BatteryInfo.signedMa(currentUa(), prefs.dischargeSign)

    /** current_now dalam uA, dari BatteryManager lalu sysfs. */
    private fun currentUa(): Int {
        val v =
            try {
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
            } catch (e: Exception) {
                Int.MIN_VALUE
            }
        if (v != Int.MIN_VALUE && v != 0) return v
        val raw = BatteryInfo.parseInt(SysNodes.read(psy + PSY_CURRENT))
        return if (raw != BatteryInfo.UNKNOWN) raw else if (v == 0) 0 else BatteryInfo.UNKNOWN
    }

    private companion object {
        const val SLOW_MS = 60_000L
        // Path asli power_supply/battery (vendor_sysfs_battery_supply). /sys/class/power_supply
        // berlabel sysfs_batteryinfo yang tidak boleh dibaca app sistem. ingres: qti_battery_charger
        // (ADSP); xm_battmngr dipakai beberapa HP sm8450 lain.
        val PSY_CANDIDATES =
            arrayOf(
                "/sys/devices/platform/soc/soc:qcom,pmic_glink/soc:qcom,pmic_glink:qcom,battery_charger/power_supply/battery/",
                "/sys/devices/platform/soc/soc:xm_battmngr/power_supply/battery/",
            )
        const val PSY_FULL = "charge_full"
        const val PSY_DESIGN = "charge_full_design"
        const val PSY_CYCLE = "cycle_count"
        const val PSY_CURRENT = "current_now"
        const val QB = "/sys/class/qcom-battery/"
        const val NODE_REAL_TYPE = QB + "real_type"
        const val NODE_POWER_MAX = QB + "power_max"
    }
}
