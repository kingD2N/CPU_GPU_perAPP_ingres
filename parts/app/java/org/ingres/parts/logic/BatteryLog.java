/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Riwayat baterai: sampel disimpan satu baris per sampel
 * ("waktu_ms,persen,suhu_x10,arus_mA,dicas,layar"), lalu diolah jadi sesi cas dan pemakaian saat
 * layar mati.
 */
public final class BatteryLog {
    /** Sampel disimpan paling lama 7 hari. */
    public static final long KEEP_MS = 7L * 24 * 60 * 60 * 1000;
    /** Sesi cas lebih pendek dari ini tidak ditampilkan. */
    public static final long MIN_SESSION_MS = 2L * 60 * 1000;
    /** Pemakaian layar mati baru dihitung bila total waktunya minimal ini. */
    public static final long MIN_IDLE_MS = 30L * 60 * 1000;
    /** Jeda antarsampel di atas ini dianggap terputus (HP mati / app berhenti). */
    public static final long MAX_GAP_MS = 3L * 60 * 60 * 1000;

    private BatteryLog() {}

    public static final class Sample {
        public final long time;
        public final int level;
        public final int temp10;
        public final int currentMa;
        public final boolean plugged;
        public final boolean screenOn;

        public Sample(long time, int level, int temp10, int currentMa, boolean plugged, boolean screenOn) {
            this.time = time;
            this.level = level;
            this.temp10 = temp10;
            this.currentMa = currentMa;
            this.plugged = plugged;
            this.screenOn = screenOn;
        }

        public String encode() {
            return time + "," + level + "," + temp10 + "," + currentMa + "," + (plugged ? 1 : 0) + "," + (screenOn ? 1 : 0);
        }

        /** null bila baris rusak. */
        public static Sample decode(String line) {
            if (line == null) return null;
            String[] p = line.trim().split(",");
            if (p.length != 6) return null;
            try {
                return new Sample(
                        Long.parseLong(p[0]),
                        Integer.parseInt(p[1]),
                        Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]),
                        "1".equals(p[4]),
                        "1".equals(p[5]));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public static final class Session {
        public final long start;
        public final long end;
        public final int levelStart;
        public final int levelEnd;
        /** Suhu tertinggi x10, Integer.MIN_VALUE bila tidak ada. */
        public final int maxTemp10;
        /** Arus cas tertinggi (mA), 0 bila tidak ada. */
        public final int maxCurrentMa;

        Session(long start, long end, int levelStart, int levelEnd, int maxTemp10, int maxCurrentMa) {
            this.start = start;
            this.end = end;
            this.levelStart = levelStart;
            this.levelEnd = levelEnd;
            this.maxTemp10 = maxTemp10;
            this.maxCurrentMa = maxCurrentMa;
        }

        public long durationMs() {
            return end - start;
        }
    }

    /** Buang sampel lebih tua dari KEEP_MS dan yang waktunya di masa depan. */
    public static List<Sample> trim(List<Sample> in, long now) {
        List<Sample> out = new ArrayList<>();
        for (Sample s : in) {
            if (s.time >= now - KEEP_MS && s.time <= now + 60_000) out.add(s);
        }
        return out;
    }

    /** Sesi cas (deretan sampel dicas yang bersambung), terbaru di depan. */
    public static List<Session> sessions(List<Sample> samples) {
        List<Session> out = new ArrayList<>();
        Sample first = null;
        Sample last = null;
        int maxT = Integer.MIN_VALUE;
        int maxI = 0;
        for (Sample s : samples) {
            boolean broken = last != null && s.time - last.time > MAX_GAP_MS;
            if (first != null && (!s.plugged || broken)) {
                add(out, first, last, maxT, maxI);
                first = null;
            }
            if (s.plugged) {
                if (first == null) {
                    first = s;
                    maxT = Integer.MIN_VALUE;
                    maxI = 0;
                }
                maxT = Math.max(maxT, s.temp10);
                maxI = Math.max(maxI, s.currentMa);
            }
            last = s;
        }
        if (first != null) add(out, first, last, maxT, maxI);
        List<Session> rev = new ArrayList<>();
        for (int i = out.size() - 1; i >= 0; i--) rev.add(out.get(i));
        return rev;
    }

    private static void add(List<Session> out, Sample first, Sample last, int maxT, int maxI) {
        if (last.time - first.time < MIN_SESSION_MS) return;
        out.add(new Session(first.time, last.time, first.level, last.level, maxT, maxI));
    }

    /**
     * Rata-rata persen baterai yang turun per jam saat layar mati dan tidak dicas, NaN bila
     * datanya belum cukup (kurang dari MIN_IDLE_MS).
     */
    public static double idleDrainPerHour(List<Sample> samples) {
        long ms = 0;
        int drop = 0;
        Sample prev = null;
        for (Sample s : samples) {
            if (prev != null) {
                long dt = s.time - prev.time;
                boolean idle = !prev.plugged && !s.plugged && !prev.screenOn && !s.screenOn;
                if (idle && dt > 0 && dt <= MAX_GAP_MS) {
                    ms += dt;
                    drop += Math.max(0, prev.level - s.level);
                }
            }
            prev = s;
        }
        if (ms < MIN_IDLE_MS) return Double.NaN;
        return drop * 3_600_000.0 / ms;
    }
}
