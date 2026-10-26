package com.nexora.core.diag;

import com.nexora.core.util.Clock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs tests one by one. A test that throws, crashes or hangs never stops the others:
 * exception/Error => FAIL, timeout => UNKNOWN, unmet prerequisite => UNKNOWN.
 */
public final class DiagnosticsRunner {
    private final long defaultTimeoutMs;
    private final Clock clock;

    public DiagnosticsRunner(long defaultTimeoutMs, Clock clock) {
        if (defaultTimeoutMs <= 0) throw new IllegalArgumentException("timeout must be > 0");
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.clock = clock;
    }

    public DiagReport run(List<DiagTest> tests) {
        List<TestResult> results = new ArrayList<TestResult>();
        Map<String, Status> byId = new HashMap<String, Status>();
        for (DiagTest t : tests) {
            TestResult r = runOne(t, byId);
            results.add(r);
            byId.put(r.id, r.status);
        }
        return new DiagReport(results);
    }

    private TestResult runOne(final DiagTest t, Map<String, Status> done) {
        for (String req : t.requires()) {
            Status s = done.get(req);
            if (s != Status.PASS) {
                String why = s == null ? "prerequisite " + req + " was not run" : "prerequisite " + req + " is " + s;
                return TestResult.unknown(t.id(), t.name(), why);
            }
        }
        long timeout = t.timeoutMs() > 0 ? t.timeoutMs() : defaultTimeoutMs;
        FutureTask<TestResult> task = new FutureTask<TestResult>(new Callable<TestResult>() {
            @Override public TestResult call() throws Exception { return t.run(); }
        });
        Thread th = new Thread(task, "diag-" + t.id());
        th.setDaemon(true);
        long t0 = clock.nowMs();
        th.start();
        TestResult r;
        try {
            TestResult got = task.get(timeout, TimeUnit.MILLISECONDS);
            if (got == null) {
                r = TestResult.fail(t.id(), t.name(), "test returned no result");
            } else {
                r = new TestResult(t.id(), t.name(), got.status, got.reason, 0, got.evidence);
            }
        } catch (TimeoutException e) {
            task.cancel(true);
            th.interrupt();
            r = TestResult.unknown(t.id(), t.name(), "timed out after " + timeout + " ms");
        } catch (ExecutionException e) {
            Throwable c = e.getCause() == null ? e : e.getCause();
            r = TestResult.fail(t.id(), t.name(), "test threw " + c.getClass().getSimpleName() + ": " + c.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            r = TestResult.unknown(t.id(), t.name(), "runner interrupted");
        }
        return r.withDuration(clock.nowMs() - t0);
    }
}
