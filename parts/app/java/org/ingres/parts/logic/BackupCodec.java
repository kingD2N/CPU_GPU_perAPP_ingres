/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts.logic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Format file cadangan setelan: teks biasa, satu setelan per baris "<tipe>:<kunci>=<nilai>",
 * tipe b (boolean), i (int), s (teks; \\ dan \n di-escape). Baris lain diabaikan, sehingga file
 * dari versi lain tetap bisa dibaca.
 */
public final class BackupCodec {
    public static final String HEADER = "# Ingres Parts backup v1";

    private BackupCodec() {}

    public static String encode(Map<String, ?> values) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (Map.Entry<String, ?> e : new TreeMap<>(values).entrySet()) {
            Object v = e.getValue();
            String k = e.getKey();
            if (!validKey(k)) continue;
            if (v instanceof Boolean) {
                sb.append("b:").append(k).append('=').append(v).append('\n');
            } else if (v instanceof Integer) {
                sb.append("i:").append(k).append('=').append(v).append('\n');
            } else if (v instanceof String) {
                sb.append("s:").append(k).append('=').append(escape((String) v)).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * @param allowed kunci yang boleh dipulihkan dan tipenya ('b', 'i', 's'); kunci lain dan tipe
     *     yang tidak cocok diabaikan
     * @return setelan yang terbaca, null bila teks bukan file cadangan Ingres Parts
     */
    public static Map<String, Object> decode(String text, Map<String, Character> allowed) {
        if (text == null || !text.startsWith("# Ingres Parts backup")) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (String raw : text.split("\n")) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.length() < 4 || line.charAt(1) != ':') continue;
            int eq = line.indexOf('=');
            if (eq < 3) continue;
            char type = line.charAt(0);
            String key = line.substring(2, eq);
            String val = line.substring(eq + 1);
            Character want = allowed.get(key);
            if (want == null || want != type) continue;
            switch (type) {
                case 'b':
                    if (val.equals("true") || val.equals("false")) out.put(key, Boolean.parseBoolean(val));
                    break;
                case 'i':
                    try {
                        out.put(key, Integer.parseInt(val));
                    } catch (NumberFormatException e) {
                        // lewati
                    }
                    break;
                case 's':
                    out.put(key, unescape(val));
                    break;
                default:
                    break;
            }
        }
        return out;
    }

    private static boolean validKey(String k) {
        return k != null && !k.isEmpty() && k.indexOf('=') < 0 && k.indexOf('\n') < 0;
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n");
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
