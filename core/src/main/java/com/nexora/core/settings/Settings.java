package com.nexora.core.settings;

import com.nexora.core.capability.Tier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Validated settings. Unknown or malformed stored values fall back to safe defaults (never crash). */
public final class Settings {
    public static final int CURRENT_VERSION = 2;
    public static final List<String> THEMES = Arrays.asList("system", "light", "dark");
    public static final List<String> LANGUAGES = Arrays.asList("en", "fa", "fa-AF");   // fa-AF = Dari
    public static final List<String> MODE_OVERRIDES = Arrays.asList("auto", "basic", "standard", "advanced");

    private final SettingsStore store;
    private final List<String> warnings = new ArrayList<String>();

    public Settings(SettingsStore store) {
        this.store = store;
        SettingsMigrator.migrate(store);
    }

    public List<String> warnings() { return warnings; }

    public String theme() { return pick("theme", THEMES, "system"); }

    public String language() { return pick("language", LANGUAGES, "en"); }

    public String modeOverride() { return pick("mode_override", MODE_OVERRIDES, "auto"); }

    public boolean isRtl() {
        String l = language();
        return l.equals("fa") || l.equals("fa-AF");
    }

    public void setTheme(String v) { set("theme", THEMES, v); }

    public void setLanguage(String v) { set("language", LANGUAGES, v); }

    public void setModeOverride(String v) { set("mode_override", MODE_OVERRIDES, v); }

    /** The user may lower the automatic tier but can never raise it above what tests allow. */
    public Tier effectiveTier(Tier automatic) {
        String o = modeOverride();
        if (o.equals("auto")) return automatic;
        Tier wanted = o.equals("basic") ? Tier.BASIC : o.equals("standard") ? Tier.STANDARD : Tier.ADVANCED;
        return Tier.lower(automatic, wanted);
    }

    private String pick(String key, List<String> allowed, String def) {
        String v = store.get(key);
        if (v == null) return def;
        if (allowed.contains(v)) return v;
        warnings.add("invalid value for " + key + ", using default");
        return def;
    }

    private void set(String key, List<String> allowed, String v) {
        if (v == null || !allowed.contains(v)) throw new IllegalArgumentException("invalid " + key + ": " + v);
        store.put(key, v);
    }
}
