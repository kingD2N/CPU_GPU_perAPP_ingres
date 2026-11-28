/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.content.Context
import android.content.SharedPreferences
import org.ingres.parts.logic.ChargeCurrent
import org.ingres.parts.logic.ColorModes
import org.ingres.parts.logic.TriggerAction
import org.ingres.parts.logic.ChargePolicy
import org.ingres.parts.logic.CoolCharge
import org.ingres.parts.logic.GameList
import org.ingres.parts.logic.ThermalProfile

/**
 * Setelan Ingres Parts, disimpan di penyimpanan device-protected supaya terbaca sebelum
 * HP dibuka kuncinya sesudah boot. Semua bawaan = tidak mengubah apa pun dari ROM.
 */
class PartsPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences("parts", Context.MODE_PRIVATE)

    var chargeMode: String
        get() = ChargePolicy.normalizeMode(prefs.getString(K_CHARGE_MODE, ChargePolicy.MODE_NORMAL))
        set(v) = prefs.edit().putString(K_CHARGE_MODE, ChargePolicy.normalizeMode(v)).apply()

    var chargeLimit: Int
        get() = ChargePolicy.clampLimit(prefs.getInt(K_CHARGE_LIMIT, ChargePolicy.DEFAULT_LIMIT))
        set(v) = prefs.edit().putInt(K_CHARGE_LIMIT, ChargePolicy.clampLimit(v)).apply()

    var bypassEnabled: Boolean
        get() = prefs.getBoolean(K_BYPASS, false)
        set(v) = prefs.edit().putBoolean(K_BYPASS, v).apply()

    var bypassGameOnly: Boolean
        get() = prefs.getBoolean(K_BYPASS_GAME_ONLY, true)
        set(v) = prefs.edit().putBoolean(K_BYPASS_GAME_ONLY, v).apply()

    /** Cas dingin: arus cas diturunkan saat baterai panas. */
    var coolEnabled: Boolean
        get() = prefs.getBoolean(K_COOL, false)
        set(v) = prefs.edit().putBoolean(K_COOL, v).apply()

    /** Suhu baterai (derajat C) mulai cas dingin. */
    var coolTemp: Int
        get() = CoolCharge.clampTemp(prefs.getInt(K_COOL_TEMP, CoolCharge.DEFAULT_TEMP))
        set(v) = prefs.edit().putInt(K_COOL_TEMP, CoolCharge.clampTemp(v)).apply()

    /** Batas daya cas cepat (perkiraan watt charger). */
    var powerLimitEnabled: Boolean
        get() = prefs.getBoolean(K_POWER_LIMIT, false)
        set(v) = prefs.edit().putBoolean(K_POWER_LIMIT, v).apply()

    var powerLimitWatts: Int
        get() = ChargeCurrent.clampWatts(prefs.getInt(K_POWER_LIMIT_W, ChargeCurrent.DEFAULT_W))
        set(v) = prefs.edit().putInt(K_POWER_LIMIT_W, ChargeCurrent.clampWatts(v)).apply()

    var thermalBase: String
        get() = ThermalProfile.fromKey(prefs.getString(K_THERMAL_BASE, null)).key
        set(v) = prefs.edit().putString(K_THERMAL_BASE, ThermalProfile.fromKey(v).key).apply()

    /** Kunci profil thermal saat game, atau ThermalProfile.KEY_FOLLOW. */
    var thermalGame: String
        get() {
            val v = prefs.getString(K_THERMAL_GAME, ThermalProfile.GAMING.key)
            return if (v == ThermalProfile.KEY_FOLLOW) v else ThermalProfile.fromKey(v).key
        }
        set(v) {
            val k = if (v == ThermalProfile.KEY_FOLLOW) v else ThermalProfile.fromKey(v).key
            prefs.edit().putString(K_THERMAL_GAME, k).apply()
        }

    var games: Set<String>
        get() = GameList.decode(prefs.getString(K_GAMES, ""))
        set(v) = prefs.edit().putString(K_GAMES, GameList.encode(v)).apply()

    var gameTouch: Boolean
        get() = prefs.getBoolean(K_GAME_TOUCH, true)
        set(v) = prefs.edit().putBoolean(K_GAME_TOUCH, v).apply()

    /** Sentuhan 480 Hz di semua aplikasi (pengganti "High touch polling rate" di Setelan). */
    var touchAlways: Boolean
        get() = prefs.getBoolean(K_TOUCH_ALWAYS, false)
        set(v) = prefs.edit().putBoolean(K_TOUCH_ALWAYS, v).apply()

    var gameOverlay: Boolean
        get() = prefs.getBoolean(K_GAME_OVERLAY, false)
        set(v) = prefs.edit().putBoolean(K_GAME_OVERLAY, v).apply()

    /** Tombol bahu (layanan gamekeyd). Bawaan menyala seperti ROM. */
    var gameKeysEnabled: Boolean
        get() = prefs.getBoolean(K_GAMEKEYS, true)
        set(v) = prefs.edit().putBoolean(K_GAMEKEYS, v).apply()

    var flatMode: Boolean
        get() = prefs.getBoolean(K_FLAT, false)
        set(v) = prefs.edit().putBoolean(K_FLAT, v).apply()

    /**
     * >0 selama arus cas (FCC) dibatasi (bypass atau cas dingin) dan belum dilepas: dipakai untuk
     * melepas batas arus setelah app atau HP mulai ulang. (Versi lama menyimpan angka arus di sini.)
     */
    var pendingFccRestore: Int
        get() = prefs.getInt(K_FCC_RESTORE, 0)
        set(v) {
            prefs.edit().putInt(K_FCC_RESTORE, v).commit()
        }

    /** Aksi tombol bahu atas/bawah di luar game (TriggerAction.*). */
    var triggerUpper: String
        get() = TriggerAction.normalize(prefs.getString(K_TRIGGER_UPPER, TriggerAction.NONE))
        set(v) = prefs.edit().putString(K_TRIGGER_UPPER, TriggerAction.normalize(v)).apply()

    var triggerLower: String
        get() = TriggerAction.normalize(prefs.getString(K_TRIGGER_LOWER, TriggerAction.NONE))
        set(v) = prefs.edit().putString(K_TRIGGER_LOWER, TriggerAction.normalize(v)).apply()

    /** Aksi saat kedua tombol bahu ditekan bersamaan (TriggerAction.*). */
    var triggerCombo: String
        get() = TriggerAction.normalize(prefs.getString(K_TRIGGER_COMBO, TriggerAction.NONE))
        set(v) = prefs.edit().putString(K_TRIGGER_COMBO, TriggerAction.normalize(v)).apply()

    /** Paket aplikasi untuk aksi "Buka aplikasi" per tombol: "upper", "lower", "combo". */
    fun triggerApp(slot: String): String = prefs.getString(K_TRIGGER_APP + slot, "") ?: ""

    fun setTriggerApp(slot: String, pkg: String) {
        prefs.edit().putString(K_TRIGGER_APP + slot, pkg).apply()
    }

    /** Game Toolbox: tab di tepi layar saat game dibuka. */
    var toolboxEnabled: Boolean
        get() = prefs.getBoolean(K_TOOLBOX, false)
        set(v) = prefs.edit().putBoolean(K_TOOLBOX, v).apply()

    var gameHideHeadsUp: Boolean
        get() = prefs.getBoolean(K_GAME_HEADSUP, false)
        set(v) = prefs.edit().putBoolean(K_GAME_HEADSUP, v).apply()

    var gameLockBrightness: Boolean
        get() = prefs.getBoolean(K_GAME_BRIGHTNESS, false)
        set(v) = prefs.edit().putBoolean(K_GAME_BRIGHTNESS, v).apply()

    /** Mode warna layar saat game, atau ColorModes.FOLLOW. */
    var gameColorMode: Int
        get() = prefs.getInt(K_GAME_COLOR, ColorModes.FOLLOW)
        set(v) = prefs.edit().putInt(K_GAME_COLOR, v).apply()

    /** Catat riwayat baterai. */
    var historyEnabled: Boolean
        get() = prefs.getBoolean(K_HISTORY, true)
        set(v) = prefs.edit().putBoolean(K_HISTORY, v).apply()

    // Nilai asli yang sedang diganti selama game (dikembalikan saat keluar game atau app mulai lagi).
    var origHeadsUp: Int
        get() = prefs.getInt(K_ORIG_HEADSUP, -1)
        set(v) {
            prefs.edit().putInt(K_ORIG_HEADSUP, v).commit()
        }

    var brightnessLocked: Boolean
        get() = prefs.getBoolean(K_ORIG_BRIGHTNESS, false)
        set(v) {
            prefs.edit().putBoolean(K_ORIG_BRIGHTNESS, v).commit()
        }

    var origColorMode: Int
        get() = prefs.getInt(K_ORIG_COLOR, ColorModes.FOLLOW)
        set(v) {
            prefs.edit().putInt(K_ORIG_COLOR, v).commit()
        }

    /** Setelan pengguna untuk file cadangan (tanpa status internal). */
    fun exportable(): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for ((k, v) in prefs.all) {
            if (k in BACKUP_TYPES && v != null) out[k] = v
        }
        return out
    }

    /** Pulihkan dari file cadangan; nilai dirapikan lagi saat dibaca lewat properti di atas. */
    fun import(values: Map<String, Any>) {
        val e = prefs.edit()
        for ((k, v) in values) {
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is String -> e.putString(k, v)
            }
        }
        e.commit()
    }

    /**
     * Tanda current_now saat baterai terpakai (-1 atau 1), 0 = belum diketahui. Dipelajari saat
     * charger tercabut, karena kernel tidak selalu memakai konvensi Android.
     */
    var dischargeSign: Int
        get() = prefs.getInt(K_DISCHARGE_SIGN, 0).coerceIn(-1, 1)
        set(v) = prefs.edit().putInt(K_DISCHARGE_SIGN, v.coerceIn(-1, 1)).apply()

    companion object {
        /** Kunci yang ikut dicadangkan dan tipenya. */
        val BACKUP_TYPES: Map<String, Char> =
            mapOf(
                "charge_mode" to 's',
                "charge_limit" to 'i',
                "bypass_enabled" to 'b',
                "bypass_game_only" to 'b',
                "cool_charge" to 'b',
                "cool_charge_temp" to 'i',
                "power_limit" to 'b',
                "power_limit_watts" to 'i',
                "thermal_base" to 's',
                "thermal_game" to 's',
                "games" to 's',
                "game_touch" to 'b',
                "game_overlay" to 'b',
                "touch_always" to 'b',
                "flat_mode" to 'b',
                "gamekeys_enabled" to 'b',
                "trigger_upper" to 's',
                "trigger_lower" to 's',
                "trigger_combo" to 's',
                "trigger_app_upper" to 's',
                "trigger_app_lower" to 's',
                "trigger_app_combo" to 's',
                "toolbox" to 'b',
                "game_headsup" to 'b',
                "game_brightness" to 'b',
                "game_color_mode" to 'i',
                "history" to 'b',
            )


        const val K_CHARGE_MODE = "charge_mode"
        const val K_CHARGE_LIMIT = "charge_limit"
        const val K_BYPASS = "bypass_enabled"
        const val K_BYPASS_GAME_ONLY = "bypass_game_only"
        const val K_COOL = "cool_charge"
        const val K_COOL_TEMP = "cool_charge_temp"
        const val K_POWER_LIMIT = "power_limit"
        const val K_POWER_LIMIT_W = "power_limit_watts"
        const val K_THERMAL_BASE = "thermal_base"
        const val K_THERMAL_GAME = "thermal_game"
        const val K_GAMES = "games"
        const val K_GAME_TOUCH = "game_touch"
        const val K_GAME_OVERLAY = "game_overlay"
        const val K_TOUCH_ALWAYS = "touch_always"
        const val K_FLAT = "flat_mode"
        const val K_GAMEKEYS = "gamekeys_enabled"
        const val K_FCC_RESTORE = "fcc_restore"
        const val K_DISCHARGE_SIGN = "discharge_sign"
        const val K_TRIGGER_UPPER = "trigger_upper"
        const val K_TRIGGER_LOWER = "trigger_lower"
        const val K_TRIGGER_COMBO = "trigger_combo"
        const val K_TRIGGER_APP = "trigger_app_"
        const val K_TOOLBOX = "toolbox"
        const val K_GAME_HEADSUP = "game_headsup"
        const val K_GAME_BRIGHTNESS = "game_brightness"
        const val K_GAME_COLOR = "game_color_mode"
        const val K_HISTORY = "history"
        const val K_ORIG_HEADSUP = "orig_headsup"
        const val K_ORIG_BRIGHTNESS = "orig_brightness_locked"
        const val K_ORIG_COLOR = "orig_color_mode"
    }
}
