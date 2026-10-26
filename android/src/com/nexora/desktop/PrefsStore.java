package com.nexora.desktop;

import android.content.Context;
import android.content.SharedPreferences;

import com.nexora.core.settings.SettingsStore;

import java.util.LinkedHashMap;
import java.util.Map;

/** SettingsStore backed by app-private SharedPreferences. */
final class PrefsStore implements SettingsStore {
    private final SharedPreferences sp;

    PrefsStore(Context c) {
        sp = c.getSharedPreferences("nexora_settings", Context.MODE_PRIVATE);
    }

    @Override public String get(String key) {
        try {
            return sp.getString(key, null);
        } catch (ClassCastException e) {
            return null;
        }
    }

    @Override public void put(String key, String value) { sp.edit().putString(key, value).apply(); }

    @Override public void remove(String key) { sp.edit().remove(key).apply(); }

    @Override public Map<String, String> all() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            if (e.getValue() instanceof String) out.put(e.getKey(), (String) e.getValue());
        }
        return out;
    }
}
