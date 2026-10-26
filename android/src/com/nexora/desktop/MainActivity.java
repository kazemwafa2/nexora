package com.nexora.desktop;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import com.nexora.core.settings.Settings;

/** Hosts the desktop. It never touches the Windows engine directly. */
public class MainActivity extends Activity {
    private DesktopView desktop;

    @Override protected void attachBaseContext(Context base) {
        String lang = new Settings(new PrefsStore(base)).language();
        super.attachBaseContext(LocaleHelper.wrap(base, lang));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Settings s = new Settings(new PrefsStore(this));
        desktop = new DesktopView(this, s);
        setContentView(desktop);
        getWindow().getDecorView().setLayoutDirection(s.isRtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

    @Override public void onBackPressed() {
        if (!desktop.handleBack()) super.onBackPressed();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (desktop != null) desktop.onTrim(level);
    }
}
