package com.nexora.core.capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TierDecision {
    public final Tier tier;
    public final List<Requirement> requirements;

    public TierDecision(Tier tier, List<Requirement> reqs) {
        this.tier = tier;
        this.requirements = Collections.unmodifiableList(new ArrayList<Requirement>(reqs));
    }

    /** Human-readable reasons why a higher tier is NOT available. */
    public List<String> blockers(Tier target) {
        List<String> out = new ArrayList<String>();
        for (Requirement r : requirements) {
            if (r.tier.ordinal() <= target.ordinal() && r.status != com.nexora.core.diag.Status.PASS) {
                out.add(r.id + " [" + r.status + "]: " + r.text);
            }
        }
        return out;
    }
}
