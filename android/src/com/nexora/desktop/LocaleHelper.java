package com.nexora.desktop;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** Applies the user-chosen language (en, fa, fa-AF = Dari) without restarting the whole process. */
final class LocaleHelper {
    private LocaleHelper() { }

    static Locale localeFor(String lang) {
        if ("fa-AF".equals(lang)) return Locale.forLanguageTag("fa-AF");
        if ("fa".equals(lang)) return Locale.forLanguageTag("fa");
        return Locale.forLanguageTag("en");
    }

    static Context wrap(Context base, String lang) {
        Locale loc = localeFor(lang);
        Locale.setDefault(loc);
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocale(loc);
        c.setLayoutDirection(loc);
        return base.createConfigurationContext(c);
    }
}
