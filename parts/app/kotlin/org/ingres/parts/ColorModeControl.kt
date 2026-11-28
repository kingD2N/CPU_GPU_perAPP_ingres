/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.content.Context
import android.content.res.Resources
import android.hardware.display.ColorDisplayManager
import android.util.Log
import org.ingres.parts.logic.ColorModes

/**
 * Mode warna layar Android (Setelan > Tampilan > Warna). Di ingres mode 256+ adalah kalibrasi
 * QDCM (Vivid, Saturated, Standard, Original, P3, sRGB). Nama mode diambil dari Setelan (overlay
 * device) supaya sama persis dengan yang dilihat pengguna di sana.
 */
object ColorModeControl {
    private const val TAG = "IngresParts"
    private const val SETTINGS_PKG = "com.android.settings"

    fun modes(ctx: Context): List<ColorModes.Mode> =
        try {
            val res = ctx.packageManager.getResourcesForApplication(SETTINGS_PKG)
            val labelsId = res.getIdentifier("config_color_mode_options_strings", "array", SETTINGS_PKG)
            val valuesId = res.getIdentifier("config_color_mode_options_values", "array", SETTINGS_PKG)
            val sys = Resources.getSystem()
            val availId = sys.getIdentifier("config_availableColorModes", "array", "android")
            ColorModes.available(
                if (valuesId != 0) res.getIntArray(valuesId) else null,
                if (labelsId != 0) res.getStringArray(labelsId) else null,
                if (availId != 0) sys.getIntArray(availId) else null,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Daftar mode warna tidak terbaca", e)
            emptyList()
        }

    fun current(ctx: Context): Int =
        try {
            ctx.getSystemService(ColorDisplayManager::class.java)?.colorMode ?: ColorModes.FOLLOW
        } catch (e: Exception) {
            ColorModes.FOLLOW
        }

    fun set(ctx: Context, mode: Int) {
        if (mode < 0) return
        try {
            ctx.getSystemService(ColorDisplayManager::class.java)?.setColorMode(mode)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengganti mode warna ke $mode", e)
        }
    }
}
