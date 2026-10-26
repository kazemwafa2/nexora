package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.capability.Tier;
import com.nexora.core.settings.*;

public class SettingsTest {
    public void testDefaults() {
        Settings s = new Settings(new SettingsStore.Memory());
        assertEquals("system", s.theme());
        assertEquals("en", s.language());
        assertEquals("auto", s.modeOverride());
        assertFalse(s.isRtl());
    }

    public void testDariAndFarsiAreRtl() {
        Settings s = new Settings(new SettingsStore.Memory());
        s.setLanguage("fa-AF");
        assertTrue(s.isRtl());
        s.setLanguage("fa");
        assertTrue(s.isRtl());
        s.setLanguage("en");
        assertFalse(s.isRtl());
    }

    public void testInvalidStoredValueFallsBackWithWarning() {
        SettingsStore st = new SettingsStore.Memory();
        st.put("schema", "2");
        st.put("theme", "neon");
        Settings s = new Settings(st);
        assertEquals("system", s.theme());
        assertEquals(1, s.warnings().size());
    }

    public void testInvalidSetterThrows() {
        final Settings s = new Settings(new SettingsStore.Memory());
        assertThrows(IllegalArgumentException.class, () -> s.setTheme("neon"));
        assertThrows(IllegalArgumentException.class, () -> s.setLanguage(null));
    }

    public void testMigrationFromVersion0() {
        SettingsStore st = new SettingsStore.Memory();
        st.put("dark_mode", "true");
        st.put("lang", "fa");
        Settings s = new Settings(st);
        assertEquals("dark", s.theme());
        assertEquals("fa", s.language());
        assertEquals("2", st.get("schema"));
        assertNull(st.get("dark_mode"));
        assertNull(st.get("lang"));
    }

    public void testMigrationIsIdempotentAndGarbageSafe() {
        SettingsStore st = new SettingsStore.Memory();
        st.put("schema", "banana");
        st.put("dark_mode", "false");
        SettingsMigrator.migrate(st);
        SettingsMigrator.migrate(st);
        assertEquals("light", st.get("theme"));
        assertEquals("2", st.get("schema"));
    }

    public void testUserOverrideCanOnlyLowerTheTier() {
        SettingsStore st = new SettingsStore.Memory();
        Settings s = new Settings(st);
        s.setModeOverride("advanced");
        assertEquals(Tier.BASIC, s.effectiveTier(Tier.BASIC));
        assertEquals(Tier.STANDARD, s.effectiveTier(Tier.STANDARD));
        s.setModeOverride("basic");
        assertEquals(Tier.BASIC, s.effectiveTier(Tier.ADVANCED));
        s.setModeOverride("auto");
        assertEquals(Tier.ADVANCED, s.effectiveTier(Tier.ADVANCED));
    }
}
