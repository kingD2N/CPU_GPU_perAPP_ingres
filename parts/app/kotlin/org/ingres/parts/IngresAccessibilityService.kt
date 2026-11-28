/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import org.ingres.parts.logic.ServiceList
import org.ingres.parts.logic.TriggerAction
import org.ingres.parts.logic.TriggerGesture

/**
 * Layanan aksesibilitas Ingres Parts, dua tugas:
 * - menyaring tombol bahu (KEYCODE_F1/F2 dari xm_gamekey) di luar game dan menjalankan aksinya;
 *   di dalam game tombol diteruskan apa adanya (ketukan layar oleh GameKeys/gamekeyd).
 * - screenshot dari Game Toolbox (performGlobalAction).
 * Dinyalakan otomatis (lihat [update]) hanya bila salah satu fitur itu dipakai.
 */
class IngresAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val gesture = TriggerGesture()
    private val tick =
        Runnable {
            dispatch(gesture.tick(SystemClock.uptimeMillis()))
            scheduleTick()
        }

    override fun onServiceConnected() {
        instance = this
        val app = application as? PartsApplication
        setKeyFilter(app != null && app.isReady && app.parts.triggersOutsideGame)
    }

    /** Saring tombol hanya bila ada aksi tombol bahu (tanpa itu tombol lain tidak lewat layanan ini). */
    fun setKeyFilter(on: Boolean) {
        val info = serviceInfo ?: return
        val f = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        val next = if (on) info.flags or f else info.flags and f.inv()
        if (next == info.flags) return
        info.flags = next
        serviceInfo = info
    }

    /**
     * Tombol bahu di luar game lewat [TriggerGesture]: tekan, tahan (volume), dan kombinasi dua
     * tombol. Tombol yang tidak diberi aksi (dan tanpa kombinasi) diteruskan apa adanya.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!TriggerAction.isTriggerKey(event.keyCode)) return false
        val app = application as? PartsApplication ?: return false
        if (!app.isReady) return false
        val c = app.parts
        val p = c.prefs
        if (c.gameActive || !c.triggersOutsideGame) {
            gesture.reset()
            handler.removeCallbacks(tick)
            return false
        }
        val combo = p.triggerCombo != TriggerAction.NONE
        val own = if (event.keyCode == TriggerAction.KEY_UPPER) p.triggerUpper else p.triggerLower
        if (!combo && own == TriggerAction.NONE) return false
        gesture.configure(combo, TriggerAction.usesHold(p.triggerUpper), TriggerAction.usesHold(p.triggerLower))
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) dispatch(gesture.onDown(event.keyCode, event.eventTime))
            KeyEvent.ACTION_UP -> dispatch(gesture.onUp(event.keyCode, event.eventTime))
        }
        scheduleTick()
        return true
    }

    private fun scheduleTick() {
        handler.removeCallbacks(tick)
        val at = gesture.nextTickAt()
        if (at >= 0) handler.postAtTime(tick, at)
    }

    private fun dispatch(events: List<TriggerGesture.Event>) {
        val app = application as? PartsApplication ?: return
        if (!app.isReady) return
        val p = app.parts.prefs
        for (e in events) {
            val upper = e.key == TriggerAction.KEY_UPPER
            when (e.type) {
                TriggerGesture.COMBO -> TriggerActions.run(this, p.triggerCombo, p.triggerApp(SLOT_COMBO))
                TriggerGesture.TAP -> {
                    val action = if (upper) p.triggerUpper else p.triggerLower
                    if (action == TriggerAction.VOLUME) TriggerActions.volume(this, true)
                    else TriggerActions.run(this, action, p.triggerApp(if (upper) SLOT_UPPER else SLOT_LOWER))
                }
                TriggerGesture.HOLD -> {
                    val action = if (upper) p.triggerUpper else p.triggerLower
                    if (action == TriggerAction.VOLUME) TriggerActions.volume(this, false)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "IngresParts"
        const val SLOT_UPPER = "upper"
        const val SLOT_LOWER = "lower"
        const val SLOT_COMBO = "combo"

        @Volatile
        var instance: IngresAccessibilityService? = null
            private set

        private fun component(ctx: Context) =
            ComponentName(ctx, IngresAccessibilityService::class.java).flattenToString()

        /** Nyalakan/matikan layanan ini di setelan aksesibilitas (hanya menulis bila berubah). */
        fun update(ctx: Context, needed: Boolean) {
            try {
                val cr = ctx.contentResolver
                val comp = component(ctx)
                val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                if (ServiceList.contains(cur, comp) == needed) return
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ServiceList.set(cur, comp, needed))
                if (needed) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                Log.i(TAG, "Layanan aksesibilitas Ingres Parts: $needed")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal mengubah layanan aksesibilitas", e)
            }
        }
    }
}
