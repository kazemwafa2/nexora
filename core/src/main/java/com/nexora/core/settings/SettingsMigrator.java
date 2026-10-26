package com.nexora.core.settings;

/**
 * Versioned, ordered, idempotent migrations. v0: "dark_mode"=true/false and "lang".
 * v1: "theme" and "lang". v2: "theme" and "language" (Dari = fa-AF).
 */
public final class SettingsMigrator {
    private SettingsMigrator() { }

    public static void migrate(SettingsStore s) {
        int v = parse(s.get("schema"));
        if (v < 1) {
            String dark = s.get("dark_mode");
            if (dark != null && s.get("theme") == null) s.put("theme", "true".equals(dark) ? "dark" : "light");
            s.remove("dark_mode");
            v = 1;
        }
        if (v < 2) {
            String lang = s.get("lang");
            if (lang != null && s.get("language") == null) s.put("language", lang);
            s.remove("lang");
            v = 2;
        }
        s.put("schema", String.valueOf(v));
    }

    private static int parse(String x) {
        if (x == null) return 0;
        try {
            int v = Integer.parseInt(x.trim());
            return v < 0 ? 0 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
