/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts.logic;

/**
 * Cas dingin: arus cas baterai (FCC, battery/constant_charge_current) diturunkan saat baterai
 * panas, lalu dilepas lagi (nilai negatif, lihat SysNodes.FCC_RELEASE) setelah dingin.
 *
 * <p>Dua tingkat dengan histeresis supaya tidak bolak-balik tiap 0,1 derajat:
 * <ul>
 *   <li>PANAS: suhu mencapai batas T, arus dibatasi {@link #HOT_UA}
 *   <li>SANGAT PANAS: suhu mencapai T+{@link #VERY_HOT_STEP}, arus dibatasi {@link #VERY_HOT_UA}
 *   <li>turun dari SANGAT PANAS ke PANAS saat suhu di bawah T+{@link #VERY_HOT_STEP}-{@link #HYSTERESIS}
 *   <li>kembali normal saat suhu turun ke T-{@link #HYSTERESIS} atau di bawahnya
 * </ul>
 */
public final class CoolCharge {
    public static final int NORMAL = 0;
    public static final int HOT = 1;
    public static final int VERY_HOT = 2;

    public static final int MIN_TEMP = 36;
    public static final int MAX_TEMP = 45;
    public static final int DEFAULT_TEMP = 40;

    public static final int VERY_HOT_STEP = 4;
    public static final int HYSTERESIS = 2;

    /** Batas arus (uA). Arus cas asli ingres sampai ~5,85 A. */
    public static final int HOT_UA = 3_000_000;
    public static final int VERY_HOT_UA = 1_500_000;

    private CoolCharge() {}

    public static int clampTemp(int t) {
        if (t < MIN_TEMP) return MIN_TEMP;
        if (t > MAX_TEMP) return MAX_TEMP;
        return t;
    }

    /**
     * @param enabled sakelar cas dingin
     * @param plugged charger tersambung (tanpa charger batas selalu dilepas)
     * @param tempC suhu baterai, NaN = belum diketahui (tingkat sekarang dipertahankan)
     * @param limitC batas suhu pengguna (dijepit ke MIN_TEMP..MAX_TEMP)
     * @param current tingkat yang sedang berlaku
     * @return NORMAL, HOT, atau VERY_HOT
     */
    public static int decide(boolean enabled, boolean plugged, float tempC, int limitC, int current) {
        if (!enabled || !plugged) return NORMAL;
        if (Float.isNaN(tempC)) return clampLevel(current);
        int t = clampTemp(limitC);
        int cur = clampLevel(current);
        if (tempC >= t + VERY_HOT_STEP) return VERY_HOT;
        if (cur == VERY_HOT && tempC >= t + VERY_HOT_STEP - HYSTERESIS) return VERY_HOT;
        if (tempC >= t) return HOT;
        if (cur != NORMAL && tempC > t - HYSTERESIS) return HOT;
        return NORMAL;
    }

    /** Batas arus (uA) untuk tingkat, atau -1 = tanpa batas. */
    public static int fccUa(int level) {
        switch (level) {
            case HOT:
                return HOT_UA;
            case VERY_HOT:
                return VERY_HOT_UA;
            default:
                return -1;
        }
    }

    private static int clampLevel(int level) {
        if (level == HOT || level == VERY_HOT) return level;
        return NORMAL;
    }
}
