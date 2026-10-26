package com.nexora.core.diag;

import java.util.regex.Pattern;

/** Removes obvious personal identifiers from text before it is stored or exported. */
public final class Redactor {
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern IMEI = Pattern.compile("(?<![0-9])[0-9]{15}(?![0-9])");
    private static final Pattern MAC = Pattern.compile("(?i)\\b([0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b");
    private static final Pattern USER_PATH = Pattern.compile("/(storage/emulated|sdcard|Users|home)/[^/\\s]+");

    private Redactor() { }

    public static String redact(String s) {
        if (s == null) return null;
        String r = EMAIL.matcher(s).replaceAll("<email>");
        r = MAC.matcher(r).replaceAll("<mac>");
        r = IMEI.matcher(r).replaceAll("<id15>");
        r = IPV4.matcher(r).replaceAll("<ip>");
        r = USER_PATH.matcher(r).replaceAll("/$1/<user>");
        return r;
    }
}
