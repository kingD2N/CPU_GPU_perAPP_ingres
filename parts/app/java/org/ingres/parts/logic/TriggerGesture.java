/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Pengenal gerakan tombol bahu (tanpa Android): tekan, tahan, dan kombinasi dua tombol.
 * Waktu dalam ms (uptime). Panggil onDown/onUp untuk tiap tombol dan tick() pada waktu
 * nextTickAt(); semua mengembalikan kejadian yang harus dijalankan.
 *
 * <ul>
 *   <li>Tanpa kombinasi dan aksi tanpa "tahan": TAP langsung saat tombol ditekan (tanpa jeda).
 *   <li>Kombinasi aktif: tombol kedua ditekan dalam {@link #COMBO_MS} = COMBO (sekali, tombol
 *       lain diabaikan sampai keduanya dilepas). Tidak ada tombol kedua = TAP setelah jendela itu
 *       atau saat dilepas.
 *   <li>Aksi dengan "tahan" (volume): dilepas sebelum {@link #HOLD_MS} = TAP; ditahan = HOLD,
 *       diulang tiap {@link #REPEAT_MS} selama masih ditahan.
 * </ul>
 */
public final class TriggerGesture {
    public static final int TAP = 1;
    public static final int HOLD = 2;
    public static final int COMBO = 3;

    public static final long COMBO_MS = 150;
    public static final long HOLD_MS = 450;
    public static final long REPEAT_MS = 200;

    /** Kejadian: type TAP/HOLD dengan key (KEY_UPPER/KEY_LOWER), atau COMBO dengan key 0. */
    public static final class Event {
        public final int type;
        public final int key;

        Event(int type, int key) {
            this.type = type;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Event && ((Event) o).type == type && ((Event) o).key == key;
        }

        @Override
        public int hashCode() {
            return type * 1000 + key;
        }

        @Override
        public String toString() {
            return (type == TAP ? "TAP" : type == HOLD ? "HOLD" : "COMBO") + ":" + key;
        }
    }

    private static final class KeyState {
        long downAt = -1;
        /** Sudah menghasilkan kejadian (TAP/COMBO): sisa tekan ini diabaikan. */
        boolean consumed;
        boolean holding;
        long lastRepeat;
    }

    private final KeyState upper = new KeyState();
    private final KeyState lower = new KeyState();

    private boolean comboEnabled;
    private boolean holdUpper;
    private boolean holdLower;

    /**
     * @param combo kombinasi dua tombol dipakai
     * @param holdUpperKey tombol atas punya aksi "tahan"
     * @param holdLowerKey tombol bawah punya aksi "tahan"
     */
    public void configure(boolean combo, boolean holdUpperKey, boolean holdLowerKey) {
        comboEnabled = combo;
        holdUpper = holdUpperKey;
        holdLower = holdLowerKey;
    }

    private KeyState state(int key) {
        return key == TriggerAction.KEY_UPPER ? upper : lower;
    }

    private KeyState other(int key) {
        return key == TriggerAction.KEY_UPPER ? lower : upper;
    }

    private boolean holds(int key) {
        return key == TriggerAction.KEY_UPPER ? holdUpper : holdLower;
    }

    public List<Event> onDown(int key, long t) {
        List<Event> out = new ArrayList<>();
        KeyState s = state(key);
        KeyState o = other(key);
        if (s.downAt >= 0) return out; // ulangan dari sistem
        s.downAt = t;
        s.consumed = false;
        s.holding = false;
        if (comboEnabled && o.downAt >= 0 && !o.consumed && t - o.downAt <= COMBO_MS) {
            s.consumed = true;
            o.consumed = true;
            out.add(new Event(COMBO, 0));
            return out;
        }
        if (comboEnabled && o.downAt >= 0) {
            // tombol lain sedang ditekan tapi di luar jendela kombinasi: tekan ini diabaikan
            s.consumed = true;
            return out;
        }
        if (!comboEnabled && !holds(key)) {
            s.consumed = true;
            out.add(new Event(TAP, key));
        }
        return out;
    }

    public List<Event> onUp(int key, long t) {
        List<Event> out = new ArrayList<>();
        KeyState s = state(key);
        if (s.downAt < 0) return out;
        if (!s.consumed && !s.holding) out.add(new Event(TAP, key));
        s.downAt = -1;
        s.consumed = false;
        s.holding = false;
        return out;
    }

    public List<Event> tick(long t) {
        List<Event> out = new ArrayList<>();
        tickKey(TriggerAction.KEY_UPPER, t, out);
        tickKey(TriggerAction.KEY_LOWER, t, out);
        return out;
    }

    private void tickKey(int key, long t, List<Event> out) {
        KeyState s = state(key);
        if (s.downAt < 0 || s.consumed) return;
        if (holds(key)) {
            if (!s.holding && t - s.downAt >= HOLD_MS) {
                s.holding = true;
                s.lastRepeat = t;
                out.add(new Event(HOLD, key));
            } else if (s.holding && t - s.lastRepeat >= REPEAT_MS) {
                s.lastRepeat = t;
                out.add(new Event(HOLD, key));
            }
        } else if (comboEnabled && t - s.downAt >= COMBO_MS) {
            s.consumed = true;
            out.add(new Event(TAP, key));
        }
    }

    /** Waktu tick berikutnya, atau -1 bila tidak perlu. */
    public long nextTickAt() {
        long a = next(TriggerAction.KEY_UPPER);
        long b = next(TriggerAction.KEY_LOWER);
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    private long next(int key) {
        KeyState s = state(key);
        if (s.downAt < 0 || s.consumed) return -1;
        if (holds(key)) return s.holding ? s.lastRepeat + REPEAT_MS : s.downAt + HOLD_MS;
        if (comboEnabled) return s.downAt + COMBO_MS;
        return -1;
    }

    /** Lepaskan semua (mis. layar mati atau masuk game). */
    public void reset() {
        upper.downAt = -1;
        lower.downAt = -1;
        upper.consumed = lower.consumed = false;
        upper.holding = lower.holding = false;
    }
}
