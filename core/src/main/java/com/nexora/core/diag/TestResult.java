package com.nexora.core.diag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TestResult {
    public final String id;
    public final String name;
    public final Status status;
    public final String reason;
    public final long durationMs;
    public final Map<String, String> evidence;

    public TestResult(String id, String name, Status status, String reason, long durationMs, Map<String, String> evidence) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.reason = reason == null ? "" : reason;
        this.durationMs = durationMs;
        this.evidence = Collections.unmodifiableMap(new LinkedHashMap<String, String>(
                evidence == null ? new LinkedHashMap<String, String>() : evidence));
    }

    public static TestResult pass(String id, String name, String reason) {
        return new TestResult(id, name, Status.PASS, reason, 0, null);
    }

    public static TestResult fail(String id, String name, String reason) {
        return new TestResult(id, name, Status.FAIL, reason, 0, null);
    }

    public static TestResult unknown(String id, String name, String reason) {
        return new TestResult(id, name, Status.UNKNOWN, reason, 0, null);
    }

    public TestResult withDuration(long ms) {
        return new TestResult(id, name, status, reason, ms, evidence);
    }

    public TestResult withEvidence(Map<String, String> ev) {
        return new TestResult(id, name, status, reason, durationMs, ev);
    }
}
