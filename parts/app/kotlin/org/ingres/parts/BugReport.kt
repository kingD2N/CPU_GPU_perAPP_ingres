/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.ingres.parts.logic.BackupCodec

/**
 * Laporan bug: satu zip di Download/IngresParts berisi info ROM dan perangkat, setelan Ingres Parts,
 * status baterai, properti sistem yang terbaca, dan logcat (izin READ_LOGS). Tidak berisi data
 * pribadi selain yang ada di log sistem; pengguna sendiri yang memilih mengirimkannya.
 */
object BugReport {
    private const val TAG = "IngresParts"
    const val FOLDER = "Download/IngresParts"

    /** Jalankan di luar thread utama. @return Uri zip, atau null bila gagal */
    fun create(ctx: Context, controller: PartsController): Uri? {
        val name = "ingres-bugreport-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".zip"
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val cr = ctx.contentResolver
        val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            cr.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    entry(zip, "info.txt", info(ctx))
                    entry(zip, "ingres-parts-settings.txt", BackupCodec.encode(controller.prefs.exportable()))
                    entry(zip, "battery.txt", battery(controller))
                    entry(zip, "getprop.txt", run(listOf("getprop")))
                    entry(zip, "logcat.txt", run(listOf("logcat", "-d", "-b", "main,system,crash,events", "-v", "threadtime")))
                }
            } ?: throw IllegalStateException("tidak bisa menulis $uri")
            cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat laporan bug", e)
            try { cr.delete(uri, null, null) } catch (ignored: Exception) {}
            null
        }
    }

    fun shareIntent(ctx: Context, uri: Uri): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Ingres bug report")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            ctx.getString(R.string.report_share),
        )

    private fun info(ctx: Context): String =
        buildString {
            appendLine("Ingres Parts bug report")
            appendLine("time: " + Date())
            appendLine("fingerprint: " + Build.FINGERPRINT)
            appendLine("device: " + Build.DEVICE + " / " + Build.MODEL)
            appendLine("android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + "), patch " + Build.VERSION.SECURITY_PATCH)
            appendLine("display: " + Build.DISPLAY)
            appendLine("kernel: " + (System.getProperty("os.version") ?: "?"))
            appendLine("uptime: " + TimeUnit.MILLISECONDS.toMinutes(SystemClock.elapsedRealtime()) + " min")
            appendLine("app: " + try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            } catch (e: Exception) {
                "?"
            })
        }

    private fun battery(controller: PartsController): String =
        buildString {
            val b = try { controller.battery.read() } catch (e: Exception) { null }
            if (b != null) {
                appendLine("level: ${b.level}%  health: ${b.healthPercent}% (code ${b.healthCode})")
                appendLine("capacity: ${b.fullMah}/${b.designMah} mAh  cycles: ${b.cycles}")
                appendLine("temp: ${b.tempC} C  voltage: ${b.voltageMv} mV  current: ${b.currentMa} mA")
                appendLine("plugged: ${b.plugged}  charger kind: ${b.chargerKind}  max ${b.chargerWatts} W")
            }
            appendLine("charge: ${controller.charge}  cool level: ${controller.coolLevel}  game: ${controller.gameActive}")
            appendLine()
            appendLine("# riwayat (100 sampel terakhir): waktu_ms,persen,suhu_x10,arus_mA,dicas,layar")
            controller.history.snapshot().takeLast(100).forEach { appendLine(it.encode()) }
        }

    private fun run(cmd: List<String>): String =
        try {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor(20, TimeUnit.SECONDS)
            text
        } catch (e: Exception) {
            "gagal menjalankan ${cmd.joinToString(" ")}: $e"
        }

    private fun entry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray())
        zip.closeEntry()
    }
}
