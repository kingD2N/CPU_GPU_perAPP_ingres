package org.ingres.parts.logic;

import java.util.Arrays;
import java.util.Set;

/**
 * Tes logika murni Ingres Parts (tanpa Android). Jalankan:
 *   javac -d out $(find app/java app/test -name "*.java")
 *   java -cp out org.ingres.parts.logic.HostTests
 */
public class HostTests {
    private static int pass = 0;

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError("GAGAL: " + name);
        pass++;
    }

    public static void main(String[] args) {
        charge();
        thermal();
        readings();
        games();
        tone();
        battery();
        cool();
        services();
        power();
        triggers();
        batteryLog();
        backup();
        colorModes();
        System.out.println("ALL PASS (" + pass + ")");
    }

    private static void charge() {
        String L = ChargePolicy.MODE_LIMIT;
        // Batas 80: berhenti di 80, lanjut lagi di 75.
        check(ChargePolicy.decide(L, 80, 80, true, false, false).suspendInput, "limit: berhenti tepat di batas");
        check(ChargePolicy.decide(L, 80, 85, true, false, false).suspendInput, "limit: di atas batas berhenti");
        check(!ChargePolicy.decide(L, 80, 79, true, false, false).suspendInput, "limit: di bawah batas tetap cas");
        check(ChargePolicy.decide(L, 80, 78, true, true, false).suspendInput, "limit: histeresis tahan suspend");
        check(!ChargePolicy.decide(L, 80, 75, true, true, false).suspendInput, "limit: lanjut cas di batas-5");
        check(!ChargePolicy.decide(L, 80, 60, false, true, false).suspendInput, "limit: turun jauh -> lepas suspend");
        check(ChargePolicy.decide(L, 80, -1, true, true, false).suspendInput, "limit: level tak diketahui -> tetap");
        // Batas dijepit ke 50..95.
        check(ChargePolicy.decide(L, 10, 50, true, false, false).suspendInput, "limit: dijepit ke 50");
        check(ChargePolicy.decide(L, 100, 95, true, false, false).suspendInput, "limit: dijepit ke 95, berhenti di 95");
        check(!ChargePolicy.decide(L, 100, 94, true, false, false).suspendInput, "limit: dijepit ke 95, 94 tetap cas");
        check(ChargePolicy.clampLimit(100) == 95 && ChargePolicy.clampLimit(10) == 50, "clampLimit");
        // Mode lain tidak pernah suspend.
        check(!ChargePolicy.decide(ChargePolicy.MODE_NORMAL, 80, 100, true, true, false).suspendInput, "normal: tanpa suspend");
        ChargePolicy.State opt = ChargePolicy.decide(ChargePolicy.MODE_OPTIMAL80, 80, 100, true, false, false);
        check(opt.nightCharging && !opt.suspendInput, "optimal80: night_charging");
        check(!ChargePolicy.decide(L, 80, 90, true, false, false).nightCharging, "limit: night mati");
        check(ChargePolicy.normalizeMode("ngawur").equals(ChargePolicy.MODE_NORMAL), "mode tak dikenal -> normal");
        check(ChargePolicy.normalizeMode(null).equals(ChargePolicy.MODE_NORMAL), "mode null -> normal");
        // Bypass hanya saat charger tersambung, dan mengalahkan suspend.
        ChargePolicy.State by = ChargePolicy.decide(L, 80, 90, true, true, true);
        check(by.bypass && !by.suspendInput, "bypass: input tetap menyala");
        check(!ChargePolicy.decide(L, 80, 90, false, false, true).bypass, "bypass: butuh charger");
        check(ChargePolicy.decide(L, 80, 90, true, false, false).equals(ChargePolicy.decide(L, 80, 90, true, false, false)), "State.equals");
    }

    private static void thermal() {
        check(ThermalProfile.fromKey(null) == ThermalProfile.SEIMBANG, "thermal: null -> seimbang");
        check(ThermalProfile.fromKey("xx") == ThermalProfile.SEIMBANG, "thermal: tak dikenal -> seimbang");
        check(ThermalProfile.SEIMBANG.sconfig == ThermalProfile.ROM_DEFAULT_SCONFIG, "thermal: seimbang = bawaan ROM");
        check(ThermalProfile.HEMAT.sconfig == 11 && ThermalProfile.HARIAN.sconfig == 0, "thermal: class0/normal");
        check(ThermalProfile.GAMING.sconfig == 13 && ThermalProfile.PERFORMA.sconfig == 10, "thermal: tgame/nolimits");
        check(ThermalProfile.isKnownSconfig(20) && !ThermalProfile.isKnownSconfig(99), "thermal: sconfig dikenal");
        check(ThermalProfile.effective("hemat", false, "gaming") == ThermalProfile.HEMAT, "thermal: tanpa game pakai utama");
        check(ThermalProfile.effective("hemat", true, "gaming") == ThermalProfile.GAMING, "thermal: game pakai profil game");
        check(ThermalProfile.effective("harian", true, ThermalProfile.KEY_FOLLOW) == ThermalProfile.HARIAN, "thermal: game ikut utama");
        check(ThermalProfile.fromKeyOrNull(ThermalProfile.KEY_FOLLOW) == null, "thermal: ikut bukan profil");
        Set<String> keys = new java.util.HashSet<>();
        for (ThermalProfile p : ThermalProfile.values()) keys.add(p.key);
        check(keys.size() == ThermalProfile.values().length, "thermal: kunci unik");
    }

    private static void readings() {
        check(Readings.parseFps("fps: 59.4 duration:1000000 frame_count:60") == 59.4f, "fps: urai");
        check(Readings.parseFps("fps: 120 duration:1") == 120f, "fps: bulat");
        check(Float.isNaN(Readings.parseFps("fps information not available")), "fps: tidak tersedia");
        check(Float.isNaN(Readings.parseFps(null)), "fps: null");
        check(Readings.formatFps(59.6f).equals("60") && Readings.formatFps(Float.NaN).equals("-"), "fps: format");
        int CPU = 0;
        float t = Readings.pickCpuTemp(new String[] {"cpu-0-0", "cpu_therm", "battery"}, new int[] {CPU, 99, 2},
                new float[] {70f, 42f, 33f}, CPU);
        check(t == 42f, "suhu: pakai cpu_therm");
        t = Readings.pickCpuTemp(new String[] {"cpu-0-0", "cpu-1-0", "battery"}, new int[] {CPU, CPU, 2},
                new float[] {40f, 50f, 33f}, CPU);
        check(t == 45f, "suhu: rata-rata CPU");
        check(Float.isNaN(Readings.pickCpuTemp(new String[] {"x"}, new int[] {5}, new float[] {30f}, CPU)), "suhu: tanpa CPU");
        check(Float.isNaN(Readings.pickCpuTemp(new String[] {"cpu_therm"}, new int[] {CPU}, new float[] {Float.NaN}, CPU)), "suhu: NaN");
        check(Readings.formatTemp(41.6f).equals("42°C") && Readings.formatTemp(Float.NaN).equals("-"), "suhu: format");
    }

    private static void games() {
        Set<String> s = GameList.decode("com.mobile.legends, com.tencent.ig,,bad,1x.y,com.a.b");
        check(s.equals(new java.util.TreeSet<>(Arrays.asList("com.a.b", "com.mobile.legends", "com.tencent.ig"))), "game: urai");
        check(GameList.encode(s).equals("com.a.b,com.mobile.legends,com.tencent.ig"), "game: simpan");
        check(GameList.decode(null).isEmpty() && GameList.decode("").isEmpty(), "game: kosong");
        check(!GameList.isValidPackage("a.b,c") && !GameList.isValidPackage("a..b") && GameList.isValidPackage("a_1.b2"), "game: validasi paket");
    }

    private static void tone() {
        short[] s = Tone.oneSecond(Tone.SAMPLE_RATE, Tone.FREQUENCY_HZ, 1f);
        check(s.length == Tone.SAMPLE_RATE, "nada: satu detik");
        check(s[0] == 0, "nada: mulai dari nol");
        // Siklus utuh: sampel sesudah ujung kembali ke awal gelombang (tanpa klik).
        double next = Math.sin(2.0 * Math.PI * Tone.FREQUENCY_HZ * Tone.SAMPLE_RATE / Tone.SAMPLE_RATE);
        check(Math.abs(next) < 1e-9, "nada: siklus utuh");
        short max = 0;
        for (short v : s) max = (short) Math.max(max, v);
        check(max > 32000, "nada: amplitudo penuh");
        check(Tone.oneSecond(1000, 10, 2f)[25] == Short.MAX_VALUE, "nada: amplitudo dijepit");
        boolean threw = false;
        try {
            Tone.oneSecond(0, 10, 1f);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "nada: parameter salah ditolak");
    }
    private static void battery() {
        int U = BatteryInfo.UNKNOWN;
        check(BatteryInfo.parseInt(" 4700000\n") == 4700000 && BatteryInfo.parseInt("x") == U
                && BatteryInfo.parseInt(null) == U && BatteryInfo.parseInt("") == U, "baterai: urai angka");
        check(BatteryInfo.toMah(4700000) == 4700 && BatteryInfo.toMah(4700) == 4700 && BatteryInfo.toMah(0) == U
                && BatteryInfo.toMah(U) == U, "baterai: uAh ke mAh");
        // ingres: dua sel seri. charge_full per rangkaian (2384 mAh), desain jumlah dua sel (4700 mAh).
        check(BatteryInfo.fullMah(2384000, 4700000, 7560) == 4768, "baterai: 2 sel seri -> kapasitas x2");
        check(BatteryInfo.healthPercent(2384000, 4700000, 7560) == 100, "baterai: 2 sel seri sehat (bukan 51%)");
        check(BatteryInfo.healthPercent(1900000, 4700000, 7700) == 81, "baterai: 2 sel seri aus");
        check(BatteryInfo.fullMah(4512000, 4700000, 7560) == 4512, "baterai: desain sudah per rangkaian, tidak dikali");
        // Satu sel (atau tegangan tak diketahui): apa adanya.
        check(BatteryInfo.healthPercent(4512000, 4700000, 3850) == 96, "baterai: 1 sel full/design");
        check(BatteryInfo.healthPercent(2384000, 4700000, U) == 51, "baterai: tegangan tak diketahui, apa adanya");
        check(BatteryInfo.healthPercent(4800000, 4700000, 3850) == 100, "baterai: full>design dibatasi 100");
        check(BatteryInfo.healthPercent(U, 4700000, 7560) == U && BatteryInfo.healthPercent(2384000, U, 7560) == U, "baterai: SOH tak diketahui");
        check(BatteryInfo.fullMah(U, 4700000, 7560) == U, "baterai: full tak diketahui");
        check(BatteryInfo.cycles(120, 5) == 120 && BatteryInfo.cycles(U, 1401) == 1401 && BatteryInfo.cycles(-1, U) == U
                && BatteryInfo.cycles(U, 1192000) == U && BatteryInfo.cycles(U, U) == U, "baterai: siklus");
        // Tanda arus dipelajari saat charger tercabut.
        check(BatteryInfo.learnDischargeSign(-800000, false) == -1, "arus: belajar tanda negatif");
        check(BatteryInfo.learnDischargeSign(800000, false) == 1, "arus: belajar tanda positif");
        check(BatteryInfo.learnDischargeSign(800000, true) == 0 && BatteryInfo.learnDischargeSign(20000, false) == 0
                && BatteryInfo.learnDischargeSign(U, false) == 0, "arus: tidak belajar");
        check(BatteryInfo.signedMa(-1500000, 0) == -1500 && BatteryInfo.signedMa(2000000, 0) == 2000, "arus: konvensi Android");
        check(BatteryInfo.signedMa(1500000, 1) == -1500 && BatteryInfo.signedMa(-2000000, 1) == 2000, "arus: kernel terbalik");
        check(BatteryInfo.signedMa(U, 1) == U, "arus: tak diketahui");
        check(Math.abs(BatteryInfo.watts(4000, -2500) - 10f) < 1e-4 && Float.isNaN(BatteryInfo.watts(U, 100)), "daya");
        check(BatteryInfo.chargerKind(false, "PD") == BatteryInfo.KIND_NONE, "charger: tidak tersambung");
        check(BatteryInfo.chargerKind(true, "PD_PPS\n") == BatteryInfo.KIND_PPS && BatteryInfo.chargerKind(true, "pd") == BatteryInfo.KIND_PD
                && BatteryInfo.chargerKind(true, "HVDCP_3P5") == BatteryInfo.KIND_QC && BatteryInfo.chargerKind(true, "DCP") == BatteryInfo.KIND_STANDARD
                && BatteryInfo.chargerKind(true, "SDP") == BatteryInfo.KIND_USB && BatteryInfo.chargerKind(true, "USB_FLOAT") == BatteryInfo.KIND_UNKNOWN
                && BatteryInfo.chargerKind(true, null) == BatteryInfo.KIND_UNKNOWN, "charger: jenis");
        check(BatteryInfo.chargerWatts(true, 120) == 120 && BatteryInfo.chargerWatts(false, 120) == U
                && BatteryInfo.chargerWatts(true, 0) == U && BatteryInfo.chargerWatts(true, 9999) == U, "charger: watt");
        check(BatteryInfo.formatVolts(4321).equals("4.32 V") && BatteryInfo.formatVolts(U).equals("-"), "format volt");
        check(BatteryInfo.formatWatts(12.34f).equals("12.3 W") && BatteryInfo.formatWatts(Float.NaN).equals("-"), "format watt");
        check(BatteryInfo.formatTempC(BatteryInfo.tempC(364)).equals("36.4°C") && BatteryInfo.formatTempC(BatteryInfo.tempC(U)).equals("-"), "format suhu");
        check(BatteryInfo.minutesLeft(90_000) == 2 && BatteryInfo.minutesLeft(1) == 1 && BatteryInfo.minutesLeft(-1) == U, "sisa waktu cas");
    }
    private static void cool() {
        int N = CoolCharge.NORMAL, H = CoolCharge.HOT, V = CoolCharge.VERY_HOT;
        check(CoolCharge.decide(true, true, 39.9f, 40, N) == N, "dingin: di bawah batas normal");
        check(CoolCharge.decide(true, true, 40f, 40, N) == H, "dingin: tepat batas -> panas");
        check(CoolCharge.decide(true, true, 44f, 40, N) == V, "dingin: batas+4 -> sangat panas");
        check(CoolCharge.decide(true, true, 44f, 40, H) == V, "dingin: panas naik ke sangat panas");
        check(CoolCharge.decide(true, true, 42.5f, 40, V) == V, "dingin: histeresis sangat panas");
        check(CoolCharge.decide(true, true, 41.9f, 40, V) == H, "dingin: sangat panas turun ke panas");
        check(CoolCharge.decide(true, true, 39f, 40, V) == H, "dingin: sangat panas di bawah batas tetap panas");
        check(CoolCharge.decide(true, true, 38.5f, 40, H) == H, "dingin: histeresis panas");
        check(CoolCharge.decide(true, true, 38f, 40, H) == N, "dingin: batas-2 kembali normal");
        check(CoolCharge.decide(false, true, 50f, 40, V) == N, "dingin: mati -> normal");
        check(CoolCharge.decide(true, false, 50f, 40, V) == N, "dingin: tanpa charger -> normal");
        check(CoolCharge.decide(true, true, Float.NaN, 40, H) == H && CoolCharge.decide(true, true, Float.NaN, 40, 7) == N,
                "dingin: suhu tak diketahui pertahankan tingkat");
        check(CoolCharge.decide(true, true, 36f, 20, N) == H, "dingin: batas dijepit ke 36");
        check(CoolCharge.clampTemp(99) == CoolCharge.MAX_TEMP && CoolCharge.clampTemp(40) == 40, "dingin: clampTemp");
        check(CoolCharge.fccUa(H) == 3_000_000 && CoolCharge.fccUa(V) == 1_500_000 && CoolCharge.fccUa(N) == -1, "dingin: arus");
    }
    private static void services() {
        String gk = "org.ingres.gamekeys/org.ingres.gamekeys.service.AccessibilityService";
        String other = "com.x/com.x.A11y";
        check(ServiceList.set(null, gk, true).equals(gk) && ServiceList.set("null", gk, true).equals(gk)
                && ServiceList.set("", gk, true).equals(gk), "a11y: nyalakan dari kosong");
        check(ServiceList.set(other, gk, true).equals(other + ":" + gk), "a11y: tambah di akhir");
        check(ServiceList.set(other + ":" + gk, gk, true).equals(other + ":" + gk), "a11y: tidak dobel");
        check(ServiceList.set(gk + ":" + other + ":" + gk, gk, false).equals(other), "a11y: matikan semua salinan");
        check(ServiceList.set("org.ingres.gamekeys/.service.AccessibilityService:" + other, gk, false).equals(other), "a11y: bentuk singkat");
        check(ServiceList.set(gk, gk, false).equals(""), "a11y: kosong setelah dimatikan");
        check(ServiceList.set(other + "::" + other, gk, false).equals(other), "a11y: rapikan daftar");
        check(ServiceList.contains("a/b.C:org.ingres.gamekeys/.service.AccessibilityService", gk)
                && !ServiceList.contains(other, gk) && !ServiceList.contains(null, gk), "a11y: contains");
    }
    private static void power() {
        int N = ChargeCurrent.NO_LIMIT;
        check(ChargeCurrent.powerCapUa(true, 60) == 6_000_000 && ChargeCurrent.powerCapMa(60) == 6000, "daya: 60 W = 6000 mA");
        check(ChargeCurrent.powerCapUa(false, 60) == N, "daya: mati = tanpa batas");
        check(ChargeCurrent.clampWatts(5) == 20 && ChargeCurrent.clampWatts(500) == 100 && ChargeCurrent.clampWatts(64) == 60, "daya: jepit & langkah 10");
        check(ChargeCurrent.target(true, false, N, 6_000_000).equals("6000000"), "fcc: batas daya saja");
        check(ChargeCurrent.target(true, false, 3_000_000, 6_000_000).equals("3000000"), "fcc: cas dingin lebih ketat");
        check(ChargeCurrent.target(true, false, 3_000_000, 2_000_000).equals("2000000"), "fcc: batas daya lebih ketat");
        check(ChargeCurrent.target(true, true, 3_000_000, 2_000_000).equals("0"), "fcc: bypass menang");
        check(ChargeCurrent.target(false, false, 3_000_000, 6_000_000) == null, "fcc: tanpa charger dilepas");
        check(ChargeCurrent.target(true, false, N, N) == null, "fcc: tanpa batas dilepas");
    }
    private static void triggers() {
        String T = TriggerAction.TORCH, N = TriggerAction.NONE;
        check(TriggerAction.forKey(TriggerAction.KEY_UPPER, false, T, N).equals(T), "bahu: atas -> senter");
        check(TriggerAction.forKey(TriggerAction.KEY_LOWER, false, T, TriggerAction.MEDIA).equals(TriggerAction.MEDIA), "bahu: bawah -> media");
        check(TriggerAction.forKey(TriggerAction.KEY_UPPER, true, T, T).equals(N), "bahu: di game diserahkan ke GameKeys");
        check(TriggerAction.forKey(25, false, T, T).equals(N), "bahu: tombol lain (volume) tidak disentuh");
        check(TriggerAction.forKey(TriggerAction.KEY_UPPER, false, "ngawur", N).equals(N) && TriggerAction.normalize(null).equals(N), "bahu: aksi tak dikenal");
        check(TriggerAction.isTriggerKey(131) && TriggerAction.isTriggerKey(132) && !TriggerAction.isTriggerKey(133), "bahu: kode tombol");
    }

    private static BatteryLog.Sample smp(long min, int lvl, int t, int ma, boolean plug, boolean scr) {
        return new BatteryLog.Sample(min * 60_000L, lvl, t, ma, plug, scr);
    }

    private static void batteryLog() {
        BatteryLog.Sample a = smp(1, 50, 365, -300, false, true);
        check(BatteryLog.Sample.decode(a.encode()).encode().equals(a.encode()), "riwayat: simpan/baca baris");
        check(BatteryLog.Sample.decode("1,2,3") == null && BatteryLog.Sample.decode("x,1,1,1,1,1") == null && BatteryLog.Sample.decode(null) == null, "riwayat: baris rusak");
        java.util.List<BatteryLog.Sample> l = new java.util.ArrayList<>();
        l.add(smp(0, 40, 330, -200, false, true));
        l.add(smp(1, 40, 340, 4000, true, true));
        l.add(smp(20, 70, 420, 5000, true, false));
        l.add(smp(40, 90, 400, 1500, true, false));
        l.add(smp(41, 90, 390, -100, false, false));
        l.add(smp(100, 85, 330, 4000, true, true));   // sesi 1 menit: dibuang
        l.add(smp(101, 86, 330, 4000, true, true));
        l.add(smp(102, 86, 330, -100, false, true));
        java.util.List<BatteryLog.Session> ss = BatteryLog.sessions(l);
        check(ss.size() == 1, "riwayat: satu sesi (sesi pendek dibuang)");
        BatteryLog.Session s0 = ss.get(0);
        check(s0.levelStart == 40 && s0.levelEnd == 90 && s0.maxTemp10 == 420 && s0.maxCurrentMa == 5000, "riwayat: isi sesi");
        check(s0.durationMs() == 39 * 60_000L, "riwayat: durasi sesi");
        // Jeda panjang memutus sesi.
        java.util.List<BatteryLog.Sample> g = new java.util.ArrayList<>();
        g.add(smp(0, 10, 300, 3000, true, true));
        g.add(smp(10, 30, 300, 3000, true, true));
        g.add(smp(500, 60, 300, 3000, true, true));
        g.add(smp(510, 80, 300, 3000, true, true));
        check(BatteryLog.sessions(g).size() == 2 && BatteryLog.sessions(g).get(0).levelStart == 60, "riwayat: jeda memutus sesi, terbaru di depan");
        // Pemakaian layar mati: 4% dalam 2 jam = 2%/jam.
        java.util.List<BatteryLog.Sample> d = new java.util.ArrayList<>();
        d.add(smp(0, 80, 300, -50, false, false));
        d.add(smp(60, 78, 300, -50, false, false));
        d.add(smp(120, 76, 300, -50, false, false));
        d.add(smp(130, 70, 300, -900, false, true));  // layar nyala: tidak dihitung
        check(Math.abs(BatteryLog.idleDrainPerHour(d) - 2.0) < 1e-9, "riwayat: pemakaian layar mati");
        java.util.List<BatteryLog.Sample> few = new java.util.ArrayList<>();
        few.add(smp(0, 80, 300, -50, false, false));
        few.add(smp(10, 80, 300, -50, false, false));
        check(Double.isNaN(BatteryLog.idleDrainPerHour(few)), "riwayat: data layar mati belum cukup");
        long now = 10L * 24 * 60 * 60 * 1000;
        java.util.List<BatteryLog.Sample> t = new java.util.ArrayList<>();
        t.add(new BatteryLog.Sample(now - BatteryLog.KEEP_MS - 1, 1, 1, 1, false, false));
        t.add(new BatteryLog.Sample(now - 1000, 1, 1, 1, false, false));
        check(BatteryLog.trim(t, now).size() == 1, "riwayat: buang sampel lama");
    }

    private static void backup() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("cool_charge", true);
        m.put("cool_charge_temp", 42);
        m.put("games", "com.a.b,com.c.d");
        m.put("note", "baris1\nbaris2 \\ akhir".replace("\\n", "\n"));
        String txt = BackupCodec.encode(m);
        check(txt.startsWith(BackupCodec.HEADER), "cadangan: kepala file");
        java.util.Map<String, Character> allow = new java.util.HashMap<>();
        allow.put("cool_charge", 'b');
        allow.put("cool_charge_temp", 'i');
        allow.put("games", 's');
        allow.put("note", 's');
        java.util.Map<String, Object> back = BackupCodec.decode(txt, allow);
        check(back.equals(m), "cadangan: simpan lalu pulihkan sama");
        allow.remove("note");
        check(!BackupCodec.decode(txt, allow).containsKey("note"), "cadangan: kunci tak dikenal diabaikan");
        allow.put("cool_charge", 'i');
        check(!BackupCodec.decode(txt, allow).containsKey("cool_charge"), "cadangan: tipe salah diabaikan");
        check(BackupCodec.decode("bukan cadangan", allow) == null, "cadangan: file lain ditolak");
        check(BackupCodec.decode(BackupCodec.HEADER + "\r\ni:cool_charge_temp=40\r\ni:x=\n".replace("\\r","\r"), allow) != null, "cadangan: baris CRLF");
    }

    private static void colorModes() {
        java.util.List<ColorModes.Mode> m = ColorModes.available(new int[] {258, 256, 257, 269}, new String[] {"Vivid", "Saturated", "Standard", "Original"}, new int[] {256, 258, 269});
        check(m.size() == 3 && m.get(0).label.equals("Vivid") && m.get(2).value == 269, "warna: hanya mode yang tersedia");
        check("Saturated".equals(ColorModes.labelOf(m, 256)) && ColorModes.labelOf(m, 999) == null, "warna: label");
        check(ColorModes.available(null, null, null).isEmpty(), "warna: tanpa data");
    }
}
