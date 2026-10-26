package com.nexora.core.capability;

import com.nexora.core.diag.Status;
import com.nexora.core.util.Tri;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure function: (measured facts, real test evidence) -> tier + reasons.
 * A requirement that is UNKNOWN does NOT qualify: nothing is ever assumed.
 */
public final class TierClassifier {
    private final TierPolicy policy;

    public TierClassifier(TierPolicy policy) { this.policy = policy; }

    public TierDecision classify(DeviceFacts f, Evidence ev) {
        List<Requirement> reqs = new ArrayList<Requirement>();

        // ---- STANDARD
        reqs.add(new Requirement("STD.LOWRAM", Tier.STANDARD,
                f.lowRamDevice == Tri.NO ? Status.PASS : f.lowRamDevice == Tri.YES ? Status.FAIL : Status.UNKNOWN,
                "device is not flagged as low-RAM"));
        reqs.add(new Requirement("STD.RAM.TOTAL", Tier.STANDARD,
                f.totalRamKb < 0 ? Status.UNKNOWN : f.totalRamKb >= policy.standardMinTotalRamKb ? Status.PASS : Status.FAIL,
                "total RAM >= " + policy.standardMinTotalRamKb + " kB"));
        reqs.add(new Requirement("STD.CORES", Tier.STANDARD,
                f.cores < 0 ? Status.UNKNOWN : f.cores >= policy.standardMinCores ? Status.PASS : Status.FAIL,
                "CPU cores >= " + policy.standardMinCores));

        // ---- ADVANCED (engine). Hardware alone is never enough: real tests must pass.
        Status abi = f.abis.isEmpty() ? Status.UNKNOWN : f.abis.contains(policy.advancedRequiredAbi) ? Status.PASS : Status.FAIL;
        reqs.add(new Requirement("ADV.ABI", Tier.ADVANCED, abi, "ABI " + policy.advancedRequiredAbi + " supported"));
        reqs.add(new Requirement("ADV.SDK", Tier.ADVANCED,
                f.sdkInt < 0 ? Status.UNKNOWN : f.sdkInt >= policy.advancedMinSdk ? Status.PASS : Status.FAIL,
                "Android API >= " + policy.advancedMinSdk));
        reqs.add(new Requirement("ADV.STORAGE", Tier.ADVANCED,
                f.freeStorageKb < 0 ? Status.UNKNOWN : f.freeStorageKb >= policy.advancedMinFreeStorageKb ? Status.PASS : Status.FAIL,
                "free storage >= " + policy.advancedMinFreeStorageKb + " kB"));
        reqs.add(new Requirement("ADV.EXEC", Tier.ADVANCED,
                f.execFromPrivateDir == Tri.YES ? Status.PASS : f.execFromPrivateDir == Tri.NO ? Status.FAIL : Status.UNKNOWN,
                "a real exec test from app-private storage succeeded"));
        for (String t : policy.advancedRequiredTests) {
            reqs.add(new Requirement("ADV.TEST." + t, Tier.ADVANCED, ev.get(t), "diagnostic " + t + " passed on this device"));
        }

        Tier tier = Tier.BASIC;
        if (allPass(reqs, Tier.STANDARD)) {
            tier = Tier.STANDARD;
            if (allPass(reqs, Tier.ADVANCED)) tier = Tier.ADVANCED;
        }
        return new TierDecision(tier, reqs);
    }

    private static boolean allPass(List<Requirement> reqs, Tier upTo) {
        for (Requirement r : reqs) {
            if (r.tier.ordinal() <= upTo.ordinal() && r.status != Status.PASS) return false;
        }
        return true;
    }
}
