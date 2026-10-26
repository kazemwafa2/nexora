package com.nexora.core.capability;

import com.nexora.core.util.Tri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Measured, brand-independent facts about a device. There is deliberately NO brand or model field:
 * the tier classifier can never depend on a name. -1 means "not measured".
 */
public final class DeviceFacts {
    public final List<String> abis;
    public final int sdkInt;
    public final long totalRamKb;
    public final long freeStorageKb;
    public final int cores;
    public final Tri lowRamDevice;
    public final Tri execFromPrivateDir;   // result of a real exec test, not a guess
    public final int vulkanLevel;          // -1 unknown
    public final int glesVersion;          // major<<16 | minor, -1 unknown

    private DeviceFacts(Builder b) {
        this.abis = Collections.unmodifiableList(new ArrayList<String>(b.abis));
        this.sdkInt = b.sdkInt;
        this.totalRamKb = b.totalRamKb;
        this.freeStorageKb = b.freeStorageKb;
        this.cores = b.cores;
        this.lowRamDevice = b.lowRamDevice;
        this.execFromPrivateDir = b.execFromPrivateDir;
        this.vulkanLevel = b.vulkanLevel;
        this.glesVersion = b.glesVersion;
    }

    public static final class Builder {
        private final List<String> abis = new ArrayList<String>();
        private int sdkInt = -1;
        private long totalRamKb = -1;
        private long freeStorageKb = -1;
        private int cores = -1;
        private Tri lowRamDevice = Tri.UNKNOWN;
        private Tri execFromPrivateDir = Tri.UNKNOWN;
        private int vulkanLevel = -1;
        private int glesVersion = -1;

        public Builder abi(String a) { abis.add(a); return this; }
        public Builder sdkInt(int v) { sdkInt = v; return this; }
        public Builder totalRamKb(long v) { totalRamKb = v; return this; }
        public Builder freeStorageKb(long v) { freeStorageKb = v; return this; }
        public Builder cores(int v) { cores = v; return this; }
        public Builder lowRamDevice(Tri v) { lowRamDevice = v; return this; }
        public Builder execFromPrivateDir(Tri v) { execFromPrivateDir = v; return this; }
        public Builder vulkanLevel(int v) { vulkanLevel = v; return this; }
        public Builder glesVersion(int v) { glesVersion = v; return this; }
        public DeviceFacts build() { return new DeviceFacts(this); }
    }
}
