/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Daftar komponen dipisah titik dua, seperti Settings.Secure enabled_accessibility_services
 * ("pkg/kelas:pkg2/.Kelas"). Dipakai untuk menyalakan/mematikan layanan aksesibilitas GameKeys
 * tanpa menyentuh layanan lain.
 */
public final class ServiceList {
    private ServiceList() {}

    /**
     * @param current isi setelan sekarang (boleh null, kosong, atau "null")
     * @param component komponen lengkap "pkg/pkg.Kelas"
     * @param enabled true = pastikan ada (sekali, di akhir), false = buang
     * @return isi setelan baru ("" bila kosong)
     */
    public static String set(String current, String component, boolean enabled) {
        List<String> out = new ArrayList<>();
        if (current != null && !current.equals("null")) {
            for (String part : current.split(":")) {
                String p = part.trim();
                if (p.isEmpty() || same(p, component) || out.contains(p)) continue;
                out.add(p);
            }
        }
        if (enabled) out.add(component);
        return String.join(":", out);
    }

    /** true bila komponen ada di daftar (bentuk lengkap atau singkat "pkg/.Kelas"). */
    public static boolean contains(String current, String component) {
        if (current == null) return false;
        for (String part : current.split(":")) {
            if (same(part.trim(), component)) return true;
        }
        return false;
    }

    private static boolean same(String a, String b) {
        return expand(a).equals(expand(b));
    }

    /** "pkg/.Kelas" -> "pkg/pkg.Kelas". */
    private static String expand(String c) {
        int slash = c.indexOf('/');
        if (slash <= 0 || slash + 1 >= c.length() || c.charAt(slash + 1) != '.') return c;
        String pkg = c.substring(0, slash);
        return pkg + "/" + pkg + c.substring(slash + 1);
    }
}
