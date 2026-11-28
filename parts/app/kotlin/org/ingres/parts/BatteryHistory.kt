/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import org.ingres.parts.logic.BatteryLog

/**
 * Perekam riwayat baterai (7 hari) di penyimpanan device-protected. Sampel diambil saat charger
 * dicolok/dicabut, layar nyala/mati, persen berubah, tiap menit selama dicas, dan paling lama tiap
 * 15 menit saat tidak dicas (bila HP bangun). Tidak memakai wakelock: saat HP tidur tidak ada
 * sampel, pemakaian layar mati dihitung dari sampel sebelum dan sesudahnya.
 * Dipanggil di thread utama; snapshot/clear aman dari thread lain.
 */
class BatteryHistory(ctx: Context, private val prefs: PartsPrefs, private val battery: BatteryReader) {
    private val file = File(ctx.createDeviceProtectedStorageContext().filesDir, "battery_history.csv")
    private val handler = Handler(Looper.getMainLooper())
    private val samples = ArrayList<BatteryLog.Sample>()
    private var loaded = false
    private var appended = 0

    private var level = -1
    private var plugged = false
    private var temp10 = Int.MIN_VALUE
    private var screenOn = true
    private var last: BatteryLog.Sample? = null

    private val tick =
        object : Runnable {
            override fun run() {
                record(false)
                if (plugged) handler.postDelayed(this, PLUGGED_MS)
            }
        }

    fun onBattery(lvl: Int, isPlugged: Boolean, tempC: Float) {
        val plugChanged = isPlugged != plugged
        level = lvl
        plugged = isPlugged
        temp10 = if (tempC.isNaN()) Int.MIN_VALUE else Math.round(tempC * 10)
        if (plugChanged) {
            handler.removeCallbacks(tick)
            if (plugged) handler.postDelayed(tick, PLUGGED_MS)
        }
        record(plugChanged)
    }

    fun onScreen(on: Boolean) {
        if (on == screenOn) return
        screenOn = on
        record(true)
    }

    private fun record(force: Boolean) {
        if (!prefs.historyEnabled || level < 0) return
        val now = System.currentTimeMillis()
        val prev = last
        if (!force && prev != null) {
            val age = now - prev.time
            val due =
                prev.level != level ||
                    (plugged && age >= PLUGGED_MS - 2_000) ||
                    (!plugged && age >= UNPLUGGED_MS)
            if (!due) return
        }
        val s = BatteryLog.Sample(now, level, temp10, battery.currentMa(), plugged, screenOn)
        last = s
        synchronized(this) {
            ensureLoaded()
            samples += s
            appended++
            try {
                if (appended >= TRIM_EVERY) {
                    appended = 0
                    val kept = BatteryLog.trim(samples, now)
                    samples.clear()
                    samples.addAll(kept)
                    file.writeText(kept.joinToString("\n", postfix = "\n") { it.encode() })
                } else {
                    file.appendText(s.encode() + "\n")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal menyimpan riwayat baterai", e)
            }
        }
    }

    @Synchronized
    fun snapshot(): List<BatteryLog.Sample> {
        ensureLoaded()
        return BatteryLog.trim(samples, System.currentTimeMillis())
    }

    @Synchronized
    fun clear() {
        samples.clear()
        loaded = true
        last = null
        file.delete()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            if (file.exists()) {
                file.forEachLine { line -> BatteryLog.Sample.decode(line)?.let { samples += it } }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Riwayat baterai tidak terbaca", e)
        }
    }

    private companion object {
        const val TAG = "IngresParts"
        const val PLUGGED_MS = 60_000L
        const val UNPLUGGED_MS = 15 * 60_000L
        const val TRIM_EVERY = 500
    }
}
