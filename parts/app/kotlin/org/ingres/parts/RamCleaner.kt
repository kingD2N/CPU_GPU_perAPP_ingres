/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * Bersihkan RAM dari Game Toolbox: hentikan proses aplikasi yang sedang di latar belakang
 * (cache), kecuali game yang sedang dimainkan, Ingres Parts, dan proses yang masih terlihat atau
 * sedang dipakai (musik, panggilan). killBackgroundProcesses tidak pernah menghentikan proses
 * persistent atau yang punya layanan latar depan.
 */
object RamCleaner {
    private const val TAG = "IngresParts"

    /** Jalankan di luar thread utama. @return perkiraan MB yang dibebaskan */
    fun clean(ctx: Context, keep: String?): Long {
        val am = ctx.getSystemService(ActivityManager::class.java) ?: return 0
        val before = availMb(am)
        val skip = setOf(ctx.packageName, keep, "android", "com.android.systemui", "com.android.phone")
        val procs = am.runningAppProcesses ?: emptyList()
        val targets = HashSet<String>()
        for (p in procs) {
            if (p.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE) continue
            for (pkg in p.pkgList ?: emptyArray()) {
                if (pkg !in skip) targets += pkg
            }
        }
        for (pkg in targets) {
            try {
                am.killBackgroundProcesses(pkg)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal menghentikan $pkg", e)
            }
        }
        Thread.sleep(600)
        Log.i(TAG, "Bersihkan RAM: ${targets.size} aplikasi")
        return (availMb(am) - before).coerceAtLeast(0)
    }

    private fun availMb(am: ActivityManager): Long {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem / (1024 * 1024)
    }
}
