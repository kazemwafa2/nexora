package com.nexora.core.capability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Thresholds live in data (config/tier_policy.properties), not in code, so they can be calibrated
 * from real device reports without a rewrite. Defaults below are PROPOSALS, not measured values.
 */
public final class TierPolicy {
    public final long standardMinTotalRamKb;
    public final int standardMinCores;
    public final int advancedMinSdk;
    public final long advancedMinFreeStorageKb;
    public final String advancedRequiredAbi;
    public final List<String> advancedRequiredTests;

    public TierPolicy(Properties p) {
        this.standardMinTotalRamKb = Long.parseLong(p.getProperty("standard.minTotalRamKb", "3000000"));
        this.standardMinCores = Integer.parseInt(p.getProperty("standard.minCores", "4"));
        this.advancedMinSdk = Integer.parseInt(p.getProperty("advanced.minSdk", "26"));
        this.advancedMinFreeStorageKb = Long.parseLong(p.getProperty("advanced.minFreeStorageKb", "1500000"));
        this.advancedRequiredAbi = p.getProperty("advanced.requiredAbi", "arm64-v8a");
        String tests = p.getProperty("advanced.requiredTests",
                "ENGINE.BOX_START,ENGINE.X64_RUN,ENGINE.WINE_SMOKE,ENGINE.GRAPHICS_SMOKE,ENGINE.STABILITY");
        List<String> l = new ArrayList<String>();
        for (String t : tests.split(",")) {
            String s = t.trim();
            if (s.length() > 0) l.add(s);
        }
        this.advancedRequiredTests = Collections.unmodifiableList(l);
    }

    public static TierPolicy defaults() { return new TierPolicy(new Properties()); }

    @Override public String toString() {
        return "TierPolicy" + Arrays.asList(standardMinTotalRamKb, standardMinCores, advancedMinSdk,
                advancedMinFreeStorageKb, advancedRequiredAbi, advancedRequiredTests);
    }
}
