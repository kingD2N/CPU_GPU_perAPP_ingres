/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts.logic;

import java.util.Locale;

/**
 * Pengolah angka info baterai (tanpa Android). Sumber di ingres (qti_battery_charger Xiaomi):
 * power_supply/battery (charge_full, charge_full_design, cycle_count, current_now) dan
 * qcom-battery (fg1_soh, fg1_cycle, real_type, power_max). Semua masukan boleh "tidak ada".
 */
public final class BatteryInfo {
    /** Nilai "tidak diketahui" untuk angka bulat. */
    public static final int UNKNOWN = Integer.MIN_VALUE;

    public static final int KIND_NONE = 0;
    public static final int KIND_UNKNOWN = 1;
    public static final int KIND_USB = 2;
    public static final int KIND_STANDARD = 3;
    public static final int KIND_QC = 4;
    public static final int KIND_PD = 5;
    public static final int KIND_PPS = 6;

    /** Di bawah arus ini (mA) baterai dianggap diam (ditahan batas cas / bypass). */
    public static final int IDLE_MA = 30;
    /** Arus minimal (mA) saat charger tercabut untuk mempelajari tanda arus keluar. */
    public static final int LEARN_MIN_MA = 50;

    private BatteryInfo() {}

    /** Isi node teks ke angka bulat, UNKNOWN bila kosong/bukan angka. */
    public static int parseInt(String raw) {
        if (raw == null) return UNKNOWN;
        String s = raw.trim();
        if (s.isEmpty()) return UNKNOWN;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }
    }

    /**
     * Kapasitas ke mAh. Kernel melaporkan uAh (4700 mAh = 4700000); nilai kecil dianggap
     * sudah mAh. UNKNOWN bila tidak masuk akal.
     */
    public static int toMah(int raw) {
        if (raw == UNKNOWN || raw <= 0) return UNKNOWN;
        return raw >= 100_000 ? raw / 1000 : raw;
    }

    /**
     * Kesehatan baterai dalam persen. Utamakan SOH fuel gauge (qcom-battery/fg1_soh, yang dipakai
     * MIUI), selain itu kapasitas penuh dibagi kapasitas desain. Dibatasi 100. UNKNOWN bila
     * tidak ada keduanya.
     */
    public static int healthPercent(int fgSoh, int fullRaw, int designRaw) {
        if (fgSoh != UNKNOWN && fgSoh > 0 && fgSoh <= 110) return Math.min(fgSoh, 100);
        int full = toMah(fullRaw);
        int design = toMah(designRaw);
        if (full == UNKNOWN || design == UNKNOWN) return UNKNOWN;
        int pct = Math.round(full * 100f / design);
        if (pct <= 0) return UNKNOWN;
        return Math.min(pct, 100);
    }

    /** Siklus cas: dari Android/power_supply dulu, lalu fuel gauge. UNKNOWN bila tidak ada. */
    public static int cycles(int fromAndroid, int fromPsy, int fromFg) {
        if (fromAndroid != UNKNOWN && fromAndroid >= 0) return fromAndroid;
        if (fromPsy != UNKNOWN && fromPsy >= 0) return fromPsy;
        if (fromFg != UNKNOWN && fromFg >= 0) return fromFg;
        return UNKNOWN;
    }

    /**
     * Mempelajari tanda arus saat baterai terpakai: saat charger tercabut baterai pasti mengeluarkan
     * arus, jadi tanda current_now saat itu = tanda "keluar". Kernel Xiaomi tidak selalu memakai
     * konvensi Android (positif = mengisi).
     *
     * @return -1 atau 1 bila bisa dipelajari dari bacaan ini, 0 bila tidak
     */
    public static int learnDischargeSign(int currentUa, boolean plugged) {
        if (plugged || currentUa == UNKNOWN) return 0;
        if (Math.abs(currentUa) < LEARN_MIN_MA * 1000) return 0;
        return currentUa < 0 ? -1 : 1;
    }

    /**
     * Arus baterai dalam mA, positif = mengisi, negatif = terpakai.
     *
     * @param dischargeSign hasil learnDischargeSign yang tersimpan; 0 = belum tahu, pakai
     *     konvensi Android (negatif = terpakai)
     */
    public static int signedMa(int currentUa, int dischargeSign) {
        if (currentUa == UNKNOWN) return UNKNOWN;
        int ma = Math.round(currentUa / 1000f);
        if (dischargeSign > 0) ma = -ma;
        return ma;
    }

    /** Daya baterai (W, tanpa tanda) dari tegangan mV dan arus mA. NaN bila tidak ada. */
    public static float watts(int voltageMv, int currentMa) {
        if (voltageMv == UNKNOWN || currentMa == UNKNOWN || voltageMv <= 0) return Float.NaN;
        return Math.abs(voltageMv / 1000f * currentMa / 1000f);
    }

    /** Jenis charger dari qcom-battery/real_type ("PD_PPS", "HVDCP_3", ...). */
    public static int chargerKind(boolean plugged, String realType) {
        if (!plugged) return KIND_NONE;
        if (realType == null) return KIND_UNKNOWN;
        String t = realType.trim().toUpperCase(Locale.ROOT);
        switch (t) {
            case "SDP":
            case "CDP":
                return KIND_USB;
            case "DCP":
                return KIND_STANDARD;
            case "PD_PPS":
                return KIND_PPS;
            case "PD":
            case "PD_DRP":
                return KIND_PD;
            default:
                return t.startsWith("HVDCP") ? KIND_QC : KIND_UNKNOWN;
        }
    }

    /** Daya maksimum charger (W) yang masuk akal, UNKNOWN bila tidak. */
    public static int chargerWatts(boolean plugged, int powerMax) {
        if (!plugged || powerMax == UNKNOWN || powerMax <= 0 || powerMax > 300) return UNKNOWN;
        return powerMax;
    }

    /** Suhu dari EXTRA_TEMPERATURE (persepuluh derajat). NaN bila tidak ada. */
    public static float tempC(int tenths) {
        if (tenths == UNKNOWN) return Float.NaN;
        return tenths / 10f;
    }

    /** 4321 mV -> "4.32 V". */
    public static String formatVolts(int mv) {
        if (mv == UNKNOWN || mv <= 0) return "-";
        return String.format(Locale.ROOT, "%.2f V", mv / 1000f);
    }

    /** 12.34 -> "12.3 W". */
    public static String formatWatts(float w) {
        if (Float.isNaN(w)) return "-";
        return String.format(Locale.ROOT, "%.1f W", w);
    }

    /** 36.4 -> "36.4°C". */
    public static String formatTempC(float c) {
        if (Float.isNaN(c)) return "-";
        return String.format(Locale.ROOT, "%.1f°C", c);
    }

    /** Sisa waktu cas (ms) ke menit dibulatkan ke atas, UNKNOWN bila tidak ada. */
    public static int minutesLeft(long ms) {
        if (ms <= 0) return UNKNOWN;
        return (int) Math.max(1, (ms + 59_999) / 60_000);
    }
}
