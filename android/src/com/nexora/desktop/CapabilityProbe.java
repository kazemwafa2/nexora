package com.nexora.desktop;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.os.Build;
import android.os.StatFs;

import com.nexora.core.capability.DeviceFacts;
import com.nexora.core.util.Tri;

/** Measures real device facts. Brand and model are deliberately never read here. */
final class CapabilityProbe {
    private CapabilityProbe() { }

    static DeviceFacts collect(Context c, Tri execFromPrivateDir) {
        DeviceFacts.Builder b = new DeviceFacts.Builder();
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis != null) {
            for (String a : abis) b.abi(a);
        }
        b.sdkInt(Build.VERSION.SDK_INT);
        b.cores(Runtime.getRuntime().availableProcessors());
        b.execFromPrivateDir(execFromPrivateDir);
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                b.totalRamKb(mi.totalMem / 1024L);
                b.lowRamDevice(Tri.of(am.isLowRamDevice()));
                ConfigurationInfo ci = am.getDeviceConfigurationInfo();
                if (ci != null) b.glesVersion(ci.reqGlEsVersion);
            }
        } catch (RuntimeException e) {
            // leave those facts unmeasured (UNKNOWN); never guess
        }
        try {
            StatFs sf = new StatFs(c.getFilesDir().getAbsolutePath());
            b.freeStorageKb(sf.getAvailableBytes() / 1024L);
        } catch (RuntimeException e) {
            // unmeasured
        }
        return b.build();
    }
}
