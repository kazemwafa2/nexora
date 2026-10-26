package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.capability.*;
import com.nexora.core.diag.Status;
import com.nexora.core.util.Tri;

import java.lang.reflect.Field;

public class TierClassifierTest {
    private final TierClassifier c = new TierClassifier(TierPolicy.defaults());

    private DeviceFacts.Builder strong() {
        return new DeviceFacts.Builder().abi("arm64-v8a").abi("armeabi-v7a").sdkInt(34).totalRamKb(8000000)
                .freeStorageKb(20000000).cores(8).lowRamDevice(Tri.NO).execFromPrivateDir(Tri.YES);
    }

    private Evidence allPass() {
        Evidence e = new Evidence();
        for (String t : TierPolicy.defaults().advancedRequiredTests) e.put(t, Status.PASS);
        return e;
    }

    public void testNothingKnownIsBasic() {
        assertEquals(Tier.BASIC, c.classify(new DeviceFacts.Builder().build(), new Evidence()).tier);
    }

    public void testStrongHardwareWithoutTestsIsOnlyStandard() {
        TierDecision d = c.classify(strong().build(), new Evidence());
        assertEquals(Tier.STANDARD, d.tier);
        assertFalse(d.blockers(Tier.ADVANCED).isEmpty());
    }

    public void testAdvancedNeedsEveryRealTest() {
        assertEquals(Tier.ADVANCED, c.classify(strong().build(), allPass()).tier);
        for (String t : TierPolicy.defaults().advancedRequiredTests) {
            Evidence e = allPass().put(t, Status.UNKNOWN);
            assertEquals(Tier.STANDARD, c.classify(strong().build(), e).tier);
            e = allPass().put(t, Status.FAIL);
            assertEquals(Tier.STANDARD, c.classify(strong().build(), e).tier);
        }
    }

    public void testBoxStartAloneDoesNotEnableAdvanced() {
        Evidence e = new Evidence().put("ENGINE.BOX_START", Status.PASS).put("ENGINE.X64_RUN", Status.PASS);
        assertEquals(Tier.STANDARD, c.classify(strong().build(), e).tier);
    }

    public void testArm32OnlyNeverAdvanced() {
        DeviceFacts f = new DeviceFacts.Builder().abi("armeabi-v7a").sdkInt(30).totalRamKb(8000000).freeStorageKb(20000000)
                .cores(8).lowRamDevice(Tri.NO).execFromPrivateDir(Tri.YES).build();
        assertEquals(Tier.STANDARD, c.classify(f, allPass()).tier);
    }

    public void testExecNotAllowedBlocksAdvanced() {
        DeviceFacts f = strong().execFromPrivateDir(Tri.NO).build();
        assertEquals(Tier.STANDARD, c.classify(f, allPass()).tier);
        f = strong().execFromPrivateDir(Tri.UNKNOWN).build();
        assertEquals(Tier.STANDARD, c.classify(f, allPass()).tier);
    }

    public void testLowRamDeviceStaysBasic() {
        assertEquals(Tier.BASIC, c.classify(strong().lowRamDevice(Tri.YES).build(), allPass()).tier);
    }

    public void testSmallRamStaysBasic() {
        assertEquals(Tier.BASIC, c.classify(strong().totalRamKb(1500000).build(), allPass()).tier);
    }

    public void testOldAndroidBlocksAdvanced() {
        assertEquals(Tier.STANDARD, c.classify(strong().sdkInt(24).build(), allPass()).tier);
    }

    public void testNoBrandOrModelFieldExists() {
        for (Field f : DeviceFacts.class.getFields()) {
            String n = f.getName().toLowerCase();
            assertFalse("brand/model/manufacturer must never be a classifier input: " + n,
                    n.contains("brand") || n.contains("model") || n.contains("manufacturer") || n.contains("vendor"));
        }
    }
}
