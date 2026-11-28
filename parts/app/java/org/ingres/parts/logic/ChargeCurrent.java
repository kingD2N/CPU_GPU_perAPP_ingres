/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts.logic;

/**
 * Batas arus cas baterai (battery/constant_charge_current, uA) gabungan: bypass, cas dingin, dan
 * batas daya cas cepat. Nilai FCC tunggal yang dikirim ke ADSP adalah batas paling ketat.
 *
 * <p>Batas daya: baterai ingres dua sel seri (~8 V), jadi arus baterai 100 mA kira-kira setara 1 W
 * dari charger (60 W = 6000 mA), sudah termasuk rugi konversi charge pump. Ini perkiraan, bukan
 * ukuran daya yang tepat.
 */
public final class ChargeCurrent {
    public static final int MIN_W = 20;
    public static final int MAX_W = 100;
    public static final int DEFAULT_W = 60;
    public static final int STEP_W = 10;
    /** uA arus baterai per watt charger. */
    public static final int UA_PER_W = 100_000;

    /** Tanpa batas: nilai negatif dilepas ke ADSP sebagai UINT_MAX. */
    public static final int NO_LIMIT = -1;

    private ChargeCurrent() {}

    public static int clampWatts(int w) {
        if (w < MIN_W) return MIN_W;
        if (w > MAX_W) return MAX_W;
        return Math.round(w / (float) STEP_W) * STEP_W;
    }

    /** Batas arus (uA) dari batas daya, atau NO_LIMIT bila sakelarnya mati. */
    public static int powerCapUa(boolean enabled, int watts) {
        return enabled ? clampWatts(watts) * UA_PER_W : NO_LIMIT;
    }

    /** Perkiraan arus (mA) untuk batas daya, untuk ditampilkan. */
    public static int powerCapMa(int watts) {
        return clampWatts(watts) * UA_PER_W / 1000;
    }

    /**
     * Nilai FCC yang harus berlaku.
     *
     * @param plugged charger tersambung
     * @param bypass bypass aktif (sudah mensyaratkan charger)
     * @param coolUa batas cas dingin (CoolCharge.fccUa), NO_LIMIT bila tidak ada
     * @param powerUa batas daya (powerCapUa), NO_LIMIT bila tidak ada
     * @return "0" untuk bypass, angka uA untuk batas paling ketat, atau null = lepas batas
     */
    public static String target(boolean plugged, boolean bypass, int coolUa, int powerUa) {
        if (bypass) return "0";
        if (!plugged) return null;
        int cap = Integer.MAX_VALUE;
        if (coolUa > 0) cap = Math.min(cap, coolUa);
        if (powerUa > 0) cap = Math.min(cap, powerUa);
        return cap == Integer.MAX_VALUE ? null : Integer.toString(cap);
    }
}
