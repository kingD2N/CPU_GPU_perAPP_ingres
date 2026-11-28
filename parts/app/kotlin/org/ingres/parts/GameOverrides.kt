/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import org.ingres.parts.logic.ColorModes

/**
 * Setelan sistem yang diganti sementara selama game: notifikasi pop-up (heads-up), kecerahan
 * otomatis, dan mode warna layar. Nilai asli disimpan (commit) sebelum diganti dan dikembalikan
 * saat keluar game, atau saat app mulai lagi bila sempat mati di tengah game.
 */
class GameOverrides(private val ctx: Context, private val prefs: PartsPrefs) {
    private val cr = ctx.contentResolver

    /** @param block sembunyikan notifikasi pop-up sekarang */
    fun headsUp(block: Boolean) {
        try {
            val orig = prefs.origHeadsUp
            if (block && orig < 0) {
                val cur = Settings.Global.getInt(cr, HEADS_UP, 1)
                prefs.origHeadsUp = cur
                if (cur != 0) Settings.Global.putInt(cr, HEADS_UP, 0)
            } else if (!block && orig >= 0) {
                Settings.Global.putInt(cr, HEADS_UP, orig)
                prefs.origHeadsUp = -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengatur notifikasi pop-up", e)
        }
    }

    /**
     * Kunci kecerahan: kecerahan otomatis dimatikan sementara dan kecerahan saat itu dipertahankan
     * (tanpa ini layar melompat ke kecerahan manual terakhir).
     */
    fun brightness(lock: Boolean) {
        try {
            val dm = ctx.getSystemService(DisplayManager::class.java)
            if (lock && !prefs.brightnessLocked) {
                val mode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, MANUAL)
                if (mode != AUTO) return
                val level = dm?.getBrightness(Display.DEFAULT_DISPLAY)
                prefs.brightnessLocked = true
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, MANUAL)
                if (level != null && !level.isNaN() && level > 0f) dm.setBrightness(Display.DEFAULT_DISPLAY, level)
            } else if (!lock && prefs.brightnessLocked) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, AUTO)
                prefs.brightnessLocked = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengatur kunci kecerahan", e)
        }
    }

    /** @param target mode warna yang diinginkan sekarang, atau ColorModes.FOLLOW */
    fun colorMode(target: Int) {
        val orig = prefs.origColorMode
        if (target != ColorModes.FOLLOW) {
            if (orig == ColorModes.FOLLOW) {
                val cur = ColorModeControl.current(ctx)
                if (cur == ColorModes.FOLLOW || cur == target) return
                prefs.origColorMode = cur
            }
            if (ColorModeControl.current(ctx) != target) ColorModeControl.set(ctx, target)
        } else if (orig != ColorModes.FOLLOW) {
            ColorModeControl.set(ctx, orig)
            prefs.origColorMode = ColorModes.FOLLOW
        }
    }

    private companion object {
        const val TAG = "IngresParts"
        /** Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED (konstanta tersembunyi). */
        const val HEADS_UP = "heads_up_notifications_enabled"
        const val AUTO = Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        const val MANUAL = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    }
}
