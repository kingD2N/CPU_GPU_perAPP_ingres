/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import org.ingres.parts.logic.ServiceList

/**
 * Tombol bahu (GameKeys, vendor/xiaomi/gamekey). Dua bagian:
 * - gamekeyd (vendor): mengambil alih layar sentuh dan mengetuk posisi yang diatur saat tombol
 *   ditekan. Dihentikan/dijalankan lewat sys.ingres.parts.gamekeys (init.ingres_parts.rc).
 * - app org.ingres.gamekeys: layanan aksesibilitas yang mengirim posisi per game ke gamekeyd.
 *   App ini tidak menangani gamekeyd yang mati (crash), jadi layanannya dimatikan dan prosesnya
 *   dihentikan SEBELUM gamekeyd dihentikan, dan baru dinyalakan lagi SESUDAH gamekeyd berjalan.
 */
object GameKeysControl {
    const val PKG = EntryPoints.GAMEKEYS_PKG
    private const val A11Y = "org.ingres.gamekeys/org.ingres.gamekeys.service.AccessibilityService"

    fun setAccessibility(ctx: Context, enabled: Boolean) {
        try {
            val cr = ctx.contentResolver
            val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val next = ServiceList.set(cur, A11Y, enabled)
            if (next != (cur ?: "")) Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            if (enabled) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengubah layanan aksesibilitas GameKeys", e)
        }
    }

    /** Hentikan proses GameKeys supaya sambungannya ke gamekeyd lama tidak dipakai lagi. */
    fun forceStop(ctx: Context) {
        try {
            ctx.getSystemService(ActivityManager::class.java)?.forceStopPackage(PKG)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menghentikan $PKG", e)
        }
    }

    private const val TAG = "IngresParts"
}
