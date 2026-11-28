/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import org.ingres.parts.logic.TriggerAction

/** Aksi tombol bahu di luar game. Dipanggil di thread utama. */
object TriggerActions {
    private const val TAG = "IngresParts"

    fun run(svc: AccessibilityService, action: String) {
        try {
            when (action) {
                TriggerAction.TORCH -> Torch.toggle(svc)
                TriggerAction.CAMERA -> camera(svc)
                TriggerAction.SCREENSHOT -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                TriggerAction.MEDIA -> media(svc)
                TriggerAction.RINGER -> ringer(svc)
                TriggerAction.LOCK -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                else -> return
            }
            tick(svc)
        } catch (e: Exception) {
            Log.e(TAG, "Aksi tombol bahu gagal: $action", e)
        }
    }

    private fun camera(ctx: Context) {
        val locked = ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val action =
            if (locked) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun media(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
    }

    /** Bunyi <-> getar (dari senyap kembali ke bunyi). */
    private fun ringer(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val next = if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        am.ringerMode = next
        val msg = if (next == AudioManager.RINGER_MODE_VIBRATE) R.string.ringer_vibrate else R.string.ringer_normal
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    private fun tick(ctx: Context) {
        try {
            ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } catch (e: Exception) {
            // tanpa getar
        }
    }

    /** Senter kamera belakang; status diikuti lewat TorchCallback (juga bila dinyalakan dari QS). */
    private object Torch {
        private var cameraId: String? = null
        private var on = false
        private var registered = false

        fun toggle(ctx: Context) {
            val cm = ctx.getSystemService(CameraManager::class.java) ?: return
            if (!registered) {
                cm.registerTorchCallback(
                    object : CameraManager.TorchCallback() {
                        override fun onTorchModeChanged(id: String, enabled: Boolean) {
                            if (id == cameraId) on = enabled
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
                registered = true
            }
            val id = cameraId ?: findFlash(cm) ?: return
            cameraId = id
            cm.setTorchMode(id, !on)
            on = !on
        }

        private fun findFlash(cm: CameraManager): String? =
            cm.cameraIdList.firstOrNull {
                val c = cm.getCameraCharacteristics(it)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
    }
}
