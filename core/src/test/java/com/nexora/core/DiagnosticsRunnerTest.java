package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.diag.*;
import com.nexora.core.util.Clock;
import com.nexora.core.util.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DiagnosticsRunnerTest {
    static abstract class T implements DiagTest {
        final String id;
        final List<String> req;
        long timeout;
        T(String id, String... req) { this.id = id; this.req = Arrays.asList(req); }
        public String id() { return id; }
        public String name() { return "test " + id; }
        public List<String> requires() { return req; }
        public long timeoutMs() { return timeout; }
    }

    static T ok(String id, String... req) {
        return new T(id, req) { public TestResult run() { return TestResult.pass(id, name(), "fine"); } };
    }

    private DiagnosticsRunner runner() { return new DiagnosticsRunner(500, Clock.SYSTEM); }

    public void testCrashInOneTestDoesNotStopOthers() {
        List<DiagTest> l = new ArrayList<DiagTest>();
        l.add(ok("A"));
        l.add(new T("B") { public TestResult run() { throw new IllegalStateException("boom"); } });
        l.add(new T("C") { public TestResult run() { throw new OutOfMemoryError("oom"); } });
        l.add(ok("D"));
        DiagReport r = runner().run(l);
        assertEquals(Status.PASS, r.statusOf("A"));
        assertEquals(Status.FAIL, r.statusOf("B"));
        assertEquals(Status.FAIL, r.statusOf("C"));
        assertEquals(Status.PASS, r.statusOf("D"));
        assertEquals(2, r.count(Status.PASS));
        assertEquals(2, r.count(Status.FAIL));
    }

    public void testHangBecomesUnknownAndRunContinues() {
        T hang = new T("H") { public TestResult run() throws Exception { Thread.sleep(60000); return null; } };
        hang.timeout = 150;
        List<DiagTest> l = new ArrayList<DiagTest>();
        l.add(hang);
        l.add(ok("N"));
        long t0 = System.currentTimeMillis();
        DiagReport r = runner().run(l);
        assertTrue("must not wait for the hung test", System.currentTimeMillis() - t0 < 5000);
        assertEquals(Status.UNKNOWN, r.statusOf("H"));
        assertEquals(Status.PASS, r.statusOf("N"));
    }

    public void testPrerequisiteGating() {
        List<DiagTest> l = new ArrayList<DiagTest>();
        l.add(new T("P1") { public TestResult run() { return TestResult.fail(id, name(), "no"); } });
        l.add(ok("Q", "P1"));
        l.add(ok("R", "MISSING"));
        DiagReport r = runner().run(l);
        assertEquals(Status.UNKNOWN, r.statusOf("Q"));
        assertEquals(Status.UNKNOWN, r.statusOf("R"));
    }

    public void testNullResultIsFailNotPass() {
        List<DiagTest> l = new ArrayList<DiagTest>();
        l.add(new T("Z") { public TestResult run() { return null; } });
        assertEquals(Status.FAIL, runner().run(l).statusOf("Z"));
    }

    @SuppressWarnings("unchecked")
    public void testJsonReportSurvivesHostileText() throws Exception {
        final String nasty = "quote\" back\\ nl\n tab\t ctl\u0001 \u2028 فارسی \ud83d\ude00 lone\ud800";
        List<DiagTest> l = new ArrayList<DiagTest>();
        l.add(new T("X") { public TestResult run() { return TestResult.fail(id, name(), nasty); } });
        DiagReport r = runner().run(l);
        Map<String, Object> m = (Map<String, Object>) Json.parse(r.toJson());
        List<Object> tests = (List<Object>) m.get("tests");
        Map<String, Object> t0 = (Map<String, Object>) tests.get(0);
        assertEquals("FAIL", t0.get("status"));
        assertTrue(((String) t0.get("reason")).startsWith("quote\" back\\ nl\n tab\t"));
        assertTrue(r.toText().contains("2. TESTED AND FAILED"));
    }

    public void testUnknownIsNeverCountedAsPass() {
        DiagReport r = new DiagReport(Collections.singletonList(TestResult.unknown("U", "u", "not run")));
        assertEquals(0, r.count(Status.PASS));
        assertEquals(1, r.count(Status.UNKNOWN));
    }
}
