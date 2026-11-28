/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ingres.parts.BugReport
import org.ingres.parts.ColorModeControl
import org.ingres.parts.PartsController
import org.ingres.parts.PartsPrefs
import org.ingres.parts.R
import org.ingres.parts.logic.BackupCodec
import org.ingres.parts.logic.BatteryLog
import org.ingres.parts.logic.ColorModes
import org.ingres.parts.logic.TriggerAction

private const val TAG = "IngresParts"

/** Dialog pilihan tunggal. */
@Composable
internal fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { (key, label) ->
                    RadioRow(label, null, key == selected) {
                        onSelect(key)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun triggerLabel(action: String): String =
    stringResource(
        when (action) {
            TriggerAction.VOLUME -> R.string.trigger_volume
            TriggerAction.OPEN_APP -> R.string.trigger_app
            TriggerAction.TORCH -> R.string.trigger_torch
            TriggerAction.CAMERA -> R.string.trigger_camera
            TriggerAction.SCREENSHOT -> R.string.trigger_screenshot
            TriggerAction.MEDIA -> R.string.trigger_media
            TriggerAction.RINGER -> R.string.trigger_ringer
            TriggerAction.LOCK -> R.string.trigger_lock
            else -> R.string.trigger_none
        },
    )

/** Nama aplikasi dari paket; paket kosong = "pilih aplikasi". */
@Composable
private fun appLabel(pkg: String): String {
    val context = LocalContext.current
    if (pkg.isEmpty()) return stringResource(R.string.trigger_app_pick)
    return remember(pkg) {
        try {
            val pm = context.packageManager
            pm.getApplicationInfo(pkg, android.content.pm.PackageManager.ApplicationInfoFlags.of(0L))
                .loadLabel(pm)
                .toString()
        } catch (e: Exception) {
            pkg
        }
    }
}

/** Deskripsi baris tombol: aksi, plus nama aplikasi untuk "Buka aplikasi". */
@Composable
private fun triggerDesc(action: String, pkg: String): String =
    if (action == TriggerAction.OPEN_APP) triggerLabel(action) + " · " + appLabel(pkg) else triggerLabel(action)

/**
 * Tombol bahu di luar game: aksi tombol atas, bawah, dan kombinasi keduanya.
 * @param onPickApp buka pemilih aplikasi untuk slot "upper" / "lower" / "combo"
 */
@Composable
internal fun TriggerActionRows(controller: PartsController, onPickApp: (String) -> Unit) {
    val prefs = controller.prefs
    var upper by remember { mutableStateOf(prefs.triggerUpper) }
    var lower by remember { mutableStateOf(prefs.triggerLower) }
    var combo by remember { mutableStateOf(prefs.triggerCombo) }
    var editing by remember { mutableStateOf<String?>(null) }
    Text(
        stringResource(R.string.trigger_outside_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
    )
    Text(
        stringResource(R.string.trigger_outside_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    ClickRow(stringResource(R.string.trigger_upper), triggerDesc(upper, prefs.triggerApp("upper"))) { editing = "upper" }
    ClickRow(stringResource(R.string.trigger_lower), triggerDesc(lower, prefs.triggerApp("lower"))) { editing = "lower" }
    ClickRow(stringResource(R.string.trigger_combo), triggerDesc(combo, prefs.triggerApp("combo"))) { editing = "combo" }
    if (upper == TriggerAction.VOLUME || lower == TriggerAction.VOLUME) {
        Text(
            stringResource(R.string.trigger_volume_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    val which = editing
    if (which != null) {
        // Volume (tekan/tahan) hanya untuk satu tombol, bukan kombinasi.
        val actions = if (which == "combo") TriggerAction.ALL.filter { it != TriggerAction.VOLUME } else TriggerAction.ALL.toList()
        ChoiceDialog(
            title =
                stringResource(
                    when (which) {
                        "upper" -> R.string.trigger_upper
                        "lower" -> R.string.trigger_lower
                        else -> R.string.trigger_combo
                    },
                ),
            options = actions.map { it to triggerLabel(it) },
            selected =
                when (which) {
                    "upper" -> upper
                    "lower" -> lower
                    else -> combo
                },
            onSelect = {
                when (which) {
                    "upper" -> {
                        upper = it; prefs.triggerUpper = it
                    }
                    "lower" -> {
                        lower = it; prefs.triggerLower = it
                    }
                    else -> {
                        combo = it; prefs.triggerCombo = it
                    }
                }
                controller.applyAll()
                // "Buka aplikasi" selalu minta pilih aplikasinya.
                if (it == TriggerAction.OPEN_APP) onPickApp(which)
            },
            onDismiss = { editing = null },
        )
    }
}

/** Tambahan Mode game: Game Toolbox, notifikasi pop-up, kunci kecerahan, mode warna saat game. */
@Composable
internal fun GameExtraRows(controller: PartsController) {
    val prefs = controller.prefs
    val context = LocalContext.current
    var toolbox by remember { mutableStateOf(prefs.toolboxEnabled) }
    var headsUp by remember { mutableStateOf(prefs.gameHideHeadsUp) }
    var brightness by remember { mutableStateOf(prefs.gameLockBrightness) }
    var color by remember { mutableIntStateOf(prefs.gameColorMode) }
    var picking by remember { mutableStateOf(false) }
    val modes = remember { ColorModeControl.modes(context) }

    SwitchRow(stringResource(R.string.toolbox_title), stringResource(R.string.toolbox_desc), toolbox) {
        toolbox = it; prefs.toolboxEnabled = it; controller.applyAll()
    }
    SwitchRow(stringResource(R.string.game_headsup), stringResource(R.string.game_headsup_desc), headsUp) {
        headsUp = it; prefs.gameHideHeadsUp = it; controller.applyAll()
    }
    SwitchRow(stringResource(R.string.game_brightness), stringResource(R.string.game_brightness_desc), brightness) {
        brightness = it; prefs.gameLockBrightness = it; controller.applyAll()
    }
    if (modes.isNotEmpty()) {
        val follow = stringResource(R.string.color_follow)
        ClickRow(stringResource(R.string.game_color), ColorModes.labelOf(modes, color) ?: follow) { picking = true }
        if (picking) {
            val options = listOf(ColorModes.FOLLOW.toString() to follow) + modes.map { it.value.toString() to it.label }
            ChoiceDialog(
                title = stringResource(R.string.game_color),
                options = options,
                selected = color.toString(),
                onSelect = { color = it.toInt(); prefs.gameColorMode = color; controller.applyAll() },
                onDismiss = { picking = false },
            )
        }
    }
}

/** Mode warna layar (sama dengan Setelan > Tampilan > Warna). */
@Composable
internal fun ColorModeRow() {
    val context = LocalContext.current
    val modes = remember { ColorModeControl.modes(context) }
    if (modes.isEmpty()) return
    var current by remember { mutableIntStateOf(ColorModeControl.current(context)) }
    var picking by remember { mutableStateOf(false) }
    ClickRow(stringResource(R.string.color_mode), ColorModes.labelOf(modes, current) ?: "-") { picking = true }
    if (picking) {
        ChoiceDialog(
            title = stringResource(R.string.color_mode),
            options = modes.map { it.value.toString() to it.label },
            selected = current.toString(),
            onSelect = {
                ColorModeControl.set(context, it.toInt())
                current = ColorModeControl.current(context)
            },
            onDismiss = { picking = false },
        )
    }
}

/** Cadangkan/pulihkan setelan dan laporan bug. */
@Composable
internal fun BackupReportSection(controller: PartsController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val ok = withContext(Dispatchers.IO) { writeBackup(context, uri, controller.prefs) }
                status = context.getString(if (ok) R.string.backup_done else R.string.backup_failed)
            }
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val n = withContext(Dispatchers.IO) { readBackup(context, uri, controller.prefs) }
                if (n < 0) {
                    status = context.getString(R.string.restore_failed)
                } else {
                    controller.applyAll()
                    (context as? Activity)?.recreate()
                }
            }
        }

    Section(stringResource(R.string.section_backup)) {
        ClickRow(stringResource(R.string.backup_title), stringResource(R.string.backup_desc)) {
            backupLauncher.launch("ingres-parts-backup.txt")
        }
        ClickRow(stringResource(R.string.restore_title), stringResource(R.string.restore_desc)) {
            restoreLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
        }
        ClickRow(
            stringResource(R.string.report_title),
            stringResource(if (busy) R.string.report_busy else R.string.report_desc),
        ) {
            if (busy) return@ClickRow
            busy = true
            scope.launch {
                val uri = withContext(Dispatchers.IO) { BugReport.create(context, controller) }
                busy = false
                if (uri == null) {
                    status = context.getString(R.string.report_failed)
                } else {
                    status = context.getString(R.string.report_done, BugReport.FOLDER)
                    try {
                        context.startActivity(BugReport.shareIntent(context, uri))
                    } catch (e: Exception) {
                        Log.w(TAG, "Tidak ada aplikasi untuk berbagi", e)
                    }
                }
            }
        }
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

private fun writeBackup(ctx: Context, uri: Uri, prefs: PartsPrefs): Boolean =
    try {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(BackupCodec.encode(prefs.exportable()).toByteArray()) } != null
    } catch (e: Exception) {
        Log.e(TAG, "Gagal mencadangkan setelan", e)
        false
    }

/** @return jumlah setelan yang dipulihkan, -1 bila file tidak cocok */
private fun readBackup(ctx: Context, uri: Uri, prefs: PartsPrefs): Int =
    try {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        val map = BackupCodec.decode(text, PartsPrefs.BACKUP_TYPES.mapValues { it.value })
        if (map == null) {
            -1
        } else {
            prefs.import(map)
            map.size
        }
    } catch (e: Exception) {
        Log.e(TAG, "Gagal memulihkan setelan", e)
        -1
    }

/** Halaman riwayat baterai: pemakaian saat layar mati, grafik 24 jam, dan sesi cas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatteryHistoryScreen(controller: PartsController, onBack: () -> Unit) {
    var samples by remember { mutableStateOf<List<BatteryLog.Sample>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(controller.prefs.historyEnabled) }
    LaunchedEffect(reload) { samples = withContext(Dispatchers.IO) { controller.history.snapshot() } }
    val sessions = remember(samples) { BatteryLog.sessions(samples) }
    val drain = remember(samples) { BatteryLog.idleDrainPerHour(samples) }
    val fmt = remember { SimpleDateFormat("EEE dd/MM HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Section(stringResource(R.string.history_idle)) {
                    Text(
                        if (drain.isNaN()) stringResource(R.string.history_idle_wait)
                        else stringResource(R.string.history_idle_value, String.format(Locale.getDefault(), "%.1f", drain)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item {
                Section(stringResource(R.string.history_chart)) {
                    HistoryChart(samples)
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.history_legend_level), color = LEVEL_COLOR, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.history_legend_temp), color = TEMP_COLOR, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Section(stringResource(R.string.history_sessions)) {
                    if (sessions.isEmpty()) {
                        Text(
                            stringResource(R.string.history_no_sessions),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    for (s in sessions.take(20)) {
                        val minutes = (s.durationMs() / 60_000).toInt()
                        val temp = if (s.maxTemp10 == Int.MIN_VALUE) "-" else String.format(Locale.getDefault(), "%.1f°C", s.maxTemp10 / 10f)
                        InfoRow(
                            fmt.format(Date(s.start)),
                            stringResource(R.string.history_session_value, s.levelStart, s.levelEnd, minutes),
                        )
                        Text(
                            stringResource(R.string.history_session_detail, temp, s.maxCurrentMa),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                        )
                    }
                }
            }
            item {
                Section(stringResource(R.string.history_settings)) {
                    SwitchRow(stringResource(R.string.history_enable), stringResource(R.string.history_enable_desc), enabled) {
                        enabled = it; controller.prefs.historyEnabled = it
                    }
                    OutlinedButton(
                        onClick = {
                            controller.history.clear()
                            reload++
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) { Text(stringResource(R.string.history_clear)) }
                }
            }
        }
    }
}

private val LEVEL_COLOR = Color(0xFF7EDB7B)
private val TEMP_COLOR = Color(0xFFFFB74D)

/** Grafik 24 jam terakhir: persen baterai (0-100) dan suhu (20-50 °C). */
@Composable
private fun HistoryChart(samples: List<BatteryLog.Sample>) {
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val now = System.currentTimeMillis()
            val from = now - DAY_MS
            for (i in 0..4) {
                val y = size.height * i / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            }
            val recent = samples.filter { it.time >= from }
            if (recent.size < 2) return@Canvas
            fun x(t: Long) = size.width * (t - from) / DAY_MS.toFloat()
            val level = Path()
            val temp = Path()
            var tempStarted = false
            recent.forEachIndexed { i, s ->
                val ly = size.height * (1f - s.level.coerceIn(0, 100) / 100f)
                if (i == 0) level.moveTo(x(s.time), ly) else level.lineTo(x(s.time), ly)
                if (s.temp10 != Int.MIN_VALUE) {
                    val c = (s.temp10 / 10f).coerceIn(20f, 50f)
                    val ty = size.height * (1f - (c - 20f) / 30f)
                    if (!tempStarted) {
                        temp.moveTo(x(s.time), ty)
                        tempStarted = true
                    } else {
                        temp.lineTo(x(s.time), ty)
                    }
                }
            }
            drawPath(level, LEVEL_COLOR, style = Stroke(width = 4f))
            drawPath(temp, TEMP_COLOR, style = Stroke(width = 3f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.history_24h_ago), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.history_now), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000
