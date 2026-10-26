package com.nexora.core.capability;

import com.nexora.core.diag.Status;

import java.util.HashMap;
import java.util.Map;

/** Real test outcomes from on-device diagnostics. Anything absent is UNKNOWN. */
public final class Evidence {
    private final Map<String, Status> map = new HashMap<String, Status>();

    public Evidence put(String testId, Status s) { map.put(testId, s); return this; }

    public Status get(String testId) {
        Status s = map.get(testId);
        return s == null ? Status.UNKNOWN : s;
    }
}
