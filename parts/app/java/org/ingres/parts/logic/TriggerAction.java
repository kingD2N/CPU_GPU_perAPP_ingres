/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts.logic;

/**
 * Aksi tombol bahu di luar game. Driver xm_gamekey mengirim KEY_F1 (tombol atas ditekan) dan
 * KEY_F2 (tombol bawah ditekan) ke Android sebagai KEYCODE_F1/F2; F3-F6 = tombol diangkat/diturunkan.
 * Di dalam game (daftar Ingres Parts) tombol diserahkan ke GameKeys (ketukan layar).
 */
public final class TriggerAction {
    public static final String NONE = "none";
    public static final String TORCH = "torch";
    public static final String CAMERA = "camera";
    public static final String SCREENSHOT = "screenshot";
    public static final String MEDIA = "media";
    public static final String RINGER = "ringer";
    public static final String LOCK = "lock";
    /** Tekan = volume media naik, tahan = turun (berulang selama ditahan). */
    public static final String VOLUME = "volume";
    /** Buka aplikasi pilihan pengguna. */
    public static final String OPEN_APP = "app";

    public static final String[] ALL = {NONE, VOLUME, OPEN_APP, TORCH, CAMERA, SCREENSHOT, MEDIA, RINGER, LOCK};

    /** android.view.KeyEvent.KEYCODE_F1 / KEYCODE_F2. */
    public static final int KEY_UPPER = 131;
    public static final int KEY_LOWER = 132;

    private TriggerAction() {}

    public static String normalize(String a) {
        if (a != null) {
            for (String x : ALL) {
                if (x.equals(a)) return x;
            }
        }
        return NONE;
    }

    /** Aksi yang membedakan tekan dan tahan. */
    public static boolean usesHold(String action) {
        return VOLUME.equals(action);
    }

    /** true bila tombol ini milik tombol bahu (ditekan), baik dipakai maupun tidak. */
    public static boolean isTriggerKey(int keyCode) {
        return keyCode == KEY_UPPER || keyCode == KEY_LOWER;
    }

    /**
     * @return aksi untuk tombol ini, atau NONE bila tombol harus diteruskan apa adanya (bukan
     *     tombol bahu, sedang main game, atau tombol itu tidak diberi aksi)
     */
    public static String forKey(int keyCode, boolean inGame, String upper, String lower) {
        if (inGame) return NONE;
        if (keyCode == KEY_UPPER) return normalize(upper);
        if (keyCode == KEY_LOWER) return normalize(lower);
        return NONE;
    }
}
