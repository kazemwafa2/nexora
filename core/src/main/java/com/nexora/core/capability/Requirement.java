package com.nexora.core.capability;

import com.nexora.core.diag.Status;

public final class Requirement {
    public final String id;
    public final Tier tier;
    public final Status status;
    public final String text;

    public Requirement(String id, Tier tier, Status status, String text) {
        this.id = id;
        this.tier = tier;
        this.status = status;
        this.text = text;
    }
}
