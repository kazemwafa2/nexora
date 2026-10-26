package com.nexora.core.license;

public final class DependencyEntry {
    public final String name;
    public final String version;
    public final String spdx;
    public final String origin;
    public final boolean verified;
    public final String scope;   // "app" (inside the APK) or "engine-pack" (downloaded/imported separately)

    public DependencyEntry(String name, String version, String spdx, String origin, boolean verified, String scope) {
        this.name = name;
        this.version = version;
        this.spdx = spdx;
        this.origin = origin;
        this.verified = verified;
        this.scope = scope;
    }
}
