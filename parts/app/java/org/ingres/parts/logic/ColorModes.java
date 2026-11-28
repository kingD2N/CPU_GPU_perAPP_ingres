/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Mode warna layar: daftar dari Setelan (config_color_mode_options_strings/values, overlay device)
 * yang juga ada di config_availableColorModes framework. Nilai 256+ = mode QDCM vendor
 * (sRGB, P3, Vivid, ...).
 */
public final class ColorModes {
    /** Mode warna saat game: ikut mode biasa. */
    public static final int FOLLOW = -1;

    private ColorModes() {}

    public static final class Mode {
        public final int value;
        public final String label;

        Mode(int value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    public static List<Mode> available(int[] values, String[] labels, int[] available) {
        List<Mode> out = new ArrayList<>();
        if (values == null || labels == null) return out;
        int n = Math.min(values.length, labels.length);
        for (int i = 0; i < n; i++) {
            if (available != null && !contains(available, values[i])) continue;
            out.add(new Mode(values[i], labels[i]));
        }
        return out;
    }

    public static String labelOf(List<Mode> modes, int value) {
        for (Mode m : modes) {
            if (m.value == value) return m.label;
        }
        return null;
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) {
            if (x == v) return true;
        }
        return false;
    }
}
