package com.nexora.core.diag;

import com.nexora.core.util.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Three-category report: tested-and-passed, tested-and-failed, not tested or unknown. */
public final class DiagReport {
    public static final int SCHEMA_VERSION = 1;
    private final List<TestResult> results;

    public DiagReport(List<TestResult> results) {
        this.results = Collections.unmodifiableList(new ArrayList<TestResult>(results));
    }

    public List<TestResult> results() { return results; }

    public int count(Status s) {
        int n = 0;
        for (TestResult r : results) if (r.status == s) n++;
        return n;
    }

    public Status statusOf(String id) {
        for (TestResult r : results) if (r.id.equals(id)) return r.status;
        return Status.UNKNOWN;
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("NEXORA DIAGNOSTICS (schema ").append(SCHEMA_VERSION).append(")\n");
        sb.append("PASS=").append(count(Status.PASS))
          .append(" FAIL=").append(count(Status.FAIL))
          .append(" UNKNOWN=").append(count(Status.UNKNOWN)).append("\n\n");
        section(sb, Status.PASS, "1. TESTED AND PASSED");
        section(sb, Status.FAIL, "2. TESTED AND FAILED");
        section(sb, Status.UNKNOWN, "3. NOT TESTED OR UNKNOWN");
        return sb.toString();
    }

    private void section(StringBuilder sb, Status s, String title) {
        sb.append("== ").append(title).append(" ==\n");
        boolean any = false;
        for (TestResult r : results) {
            if (r.status != s) continue;
            any = true;
            sb.append("- ").append(r.id).append("  ").append(r.name).append("\n    reason: ").append(r.reason).append("\n");
        }
        if (!any) sb.append("(none)\n");
        sb.append("\n");
    }

    public String toJson() {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("schema_version", Long.valueOf(SCHEMA_VERSION));
        Map<String, Object> sum = new LinkedHashMap<String, Object>();
        sum.put("pass", Long.valueOf(count(Status.PASS)));
        sum.put("fail", Long.valueOf(count(Status.FAIL)));
        sum.put("unknown", Long.valueOf(count(Status.UNKNOWN)));
        root.put("summary", sum);
        List<Object> tests = new ArrayList<Object>();
        for (TestResult r : results) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", r.id);
            m.put("name", r.name);
            m.put("status", r.status.name());
            m.put("reason", r.reason);
            m.put("duration_ms", Long.valueOf(r.durationMs));
            m.put("evidence", new LinkedHashMap<String, Object>(r.evidence));
            tests.add(m);
        }
        root.put("tests", tests);
        return Json.stringify(root);
    }
}
