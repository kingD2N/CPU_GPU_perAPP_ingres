/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.ingres.parts.logic.ThermalProfile

/**
 * Game Toolbox: tab tipis di tepi kanan layar selama game di daftar terbuka. Diketuk = panel
 * pintasan (notifikasi pop-up, kunci kecerahan, overlay FPS, sentuhan 480 Hz, profil thermal,
 * bersihkan RAM, screenshot). Semua pintasan mengubah setelan Mode game yang sama, jadi
 * tersimpan untuk game berikutnya. Dipanggil di thread utama.
 */
class GameToolbox(ctx: Context, private val controller: PartsController) {
    private val ctx = ContextThemeWrapper(ctx, android.R.style.Theme_DeviceDefault)
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var handle: View? = null
    private var panel: LinearLayout? = null

    fun show() {
        if (handle != null) return
        val bar =
            View(ctx).apply {
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(3).toFloat()
                        setColor(0xCC7EDB7B.toInt())
                    }
            }
        val touch =
            FrameLayout(ctx).apply {
                addView(bar, FrameLayout.LayoutParams(dp(5), dp(64), Gravity.END or Gravity.CENTER_VERTICAL))
                setOnClickListener { if (panel == null) showPanel() else hidePanel() }
                contentDescription = ctx.getString(R.string.toolbox_title)
            }
        val lp =
            overlayParams(dp(22), dp(72)).apply {
                gravity = Gravity.END or Gravity.TOP
                y = ctx.resources.displayMetrics.heightPixels / 3
            }
        try {
            wm.addView(touch, lp)
            handle = touch
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menampilkan Game Toolbox", e)
        }
    }

    fun hide() {
        hidePanel()
        val v = handle ?: return
        remove(v)
        handle = null
    }

    private fun showPanel() {
        val p =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(6), dp(8), dp(6), dp(8))
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        setColor(0xE6202620.toInt())
                    }
                setOnTouchListener { _, ev ->
                    if (ev.action == MotionEvent.ACTION_OUTSIDE) hidePanel()
                    false
                }
            }
        fill(p)
        val lp =
            overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
                flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = dp(26)
            }
        try {
            wm.addView(p, lp)
            panel = p
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuka panel Game Toolbox", e)
        }
    }

    private fun hidePanel() {
        val p = panel ?: return
        remove(p)
        panel = null
    }

    private fun refresh() {
        val p = panel ?: return
        p.removeAllViews()
        fill(p)
    }

    private fun fill(p: LinearLayout) {
        val prefs = controller.prefs
        p.addView(title())
        row(p, R.string.toolbox_headsup, onOff(prefs.gameHideHeadsUp, R.string.toolbox_blocked, R.string.toolbox_shown)) {
            prefs.gameHideHeadsUp = !prefs.gameHideHeadsUp
            changed()
        }
        row(p, R.string.toolbox_brightness, onOff(prefs.gameLockBrightness, R.string.toolbox_locked, R.string.toolbox_auto)) {
            prefs.gameLockBrightness = !prefs.gameLockBrightness
            changed()
        }
        row(p, R.string.toolbox_overlay, onOff(prefs.gameOverlay, R.string.toolbox_on, R.string.toolbox_off)) {
            prefs.gameOverlay = !prefs.gameOverlay
            changed()
        }
        row(p, R.string.toolbox_touch, onOff(prefs.gameTouch, R.string.toolbox_on, R.string.toolbox_off)) {
            prefs.gameTouch = !prefs.gameTouch
            changed()
        }
        row(p, R.string.toolbox_thermal, thermalLabel(prefs.thermalGame)) {
            prefs.thermalGame = nextThermal(prefs.thermalGame)
            changed()
        }
        row(p, R.string.toolbox_clean_ram, null) { cleanRam() }
        row(p, R.string.toolbox_screenshot, null) { screenshot() }
    }

    private fun changed() {
        controller.applyAll()
        refresh()
    }

    private fun cleanRam() {
        val keep = controller.topPackage
        Toast.makeText(ctx, R.string.toolbox_cleaning, Toast.LENGTH_SHORT).show()
        Thread {
            val mb = try { RamCleaner.clean(ctx, keep) } catch (e: Exception) { 0L }
            main.post { Toast.makeText(ctx, ctx.getString(R.string.toolbox_cleaned, mb), Toast.LENGTH_SHORT).show() }
        }.start()
    }

    private fun screenshot() {
        hidePanel()
        main.postDelayed({
            val svc = IngresAccessibilityService.instance
            if (svc == null || !svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)) {
                Toast.makeText(ctx, R.string.toolbox_screenshot_failed, Toast.LENGTH_SHORT).show()
            }
        }, 400)
    }

    private fun title() =
        TextView(ctx).apply {
            text = ctx.getString(R.string.toolbox_title)
            setTextColor(0xFF7EDB7B.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(12), dp(4), dp(12), dp(6))
        }

    private fun row(p: LinearLayout, label: Int, value: String?, onClick: () -> Unit) {
        val r =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumWidth = dp(220)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                isClickable = true
                val tv = TypedValue()
                if (ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
                    foreground = ctx.getDrawable(tv.resourceId)
                }
                setOnClickListener { onClick() }
            }
        r.addView(
            TextView(ctx).apply {
                text = ctx.getString(label)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (value != null) {
            r.addView(
                TextView(ctx).apply {
                    text = value
                    setTextColor(0xFF7EDB7B.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(dp(12), 0, 0, 0)
                },
            )
        }
        p.addView(r)
    }

    private fun onOff(on: Boolean, onRes: Int, offRes: Int) = ctx.getString(if (on) onRes else offRes)

    private fun thermalLabel(key: String): String {
        val p = ThermalProfile.fromKeyOrNull(key) ?: return ctx.getString(R.string.toolbox_thermal_follow)
        return ctx.getString(
            when (p) {
                ThermalProfile.HEMAT -> R.string.thermal_hemat
                ThermalProfile.HARIAN -> R.string.thermal_harian
                ThermalProfile.SEIMBANG -> R.string.thermal_seimbang
                ThermalProfile.GAMING -> R.string.thermal_gaming
                ThermalProfile.PERFORMA -> R.string.thermal_performa
            },
        )
    }

    /** Ikut profil utama -> Hemat -> ... -> Performa -> Ikut profil utama. */
    private fun nextThermal(key: String): String {
        val all = listOf(ThermalProfile.KEY_FOLLOW) + ThermalProfile.values().map { it.key }
        val i = all.indexOf(key)
        return all[(i + 1) % all.size]
    }

    private fun overlayParams(w: Int, h: Int) =
        WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "IngresPartsToolbox"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

    private fun remove(v: View) {
        try {
            wm.removeViewImmediate(v)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menutup Game Toolbox", e)
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    private companion object {
        const val TAG = "IngresParts"
    }
}
