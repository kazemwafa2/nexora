package com.nexora.core.settings;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal key-value storage; Android implements it with SharedPreferences or a file. */
public interface SettingsStore {
    String get(String key);

    void put(String key, String value);

    void remove(String key);

    Map<String, String> all();

    final class Memory implements SettingsStore {
        private final Map<String, String> m = new LinkedHashMap<String, String>();

        @Override public String get(String key) { return m.get(key); }

        @Override public void put(String key, String value) { m.put(key, value); }

        @Override public void remove(String key) { m.remove(key); }

        @Override public Map<String, String> all() { return new LinkedHashMap<String, String>(m); }
    }
}
