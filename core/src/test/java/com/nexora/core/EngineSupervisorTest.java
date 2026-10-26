package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.engine.*;
import com.nexora.core.util.Clock;

import java.util.ArrayList;
import java.util.Collections;

public class EngineSupervisorTest {
    static final class FakeClock implements Clock {
        long now = 1000000;
        public long nowMs() { return now; }
    }

    private LaunchRequest req() {
        return new LaunchRequest("c1", "C:\\apps\\test.exe", Collections.<String>emptyList(), 800, 600);
    }

    private EngineSupervisor sup(FakeEngine e, FakeClock c) {
        return new EngineSupervisor(e, c, 3, 600000L, 1000L, 60000L, 300L);
    }

    public void testLaunchOk() {
        FakeEngine e = new FakeEngine(EngineState.READY, new FaultPlan());
        EngineResult<String> r = sup(e, new FakeClock()).launch(req());
        assertTrue(r.isOk());
        assertEquals(EngineState.RUNNING, e.state());
    }

    public void testEngineExceptionNeverReachesCaller() {
        FaultPlan f = new FaultPlan();
        f.throwOnLaunch = true;
        EngineResult<String> r = sup(new FakeEngine(EngineState.READY, f), new FakeClock()).launch(req());
        assertFalse(r.isOk());
        assertEquals(EngineErrorCode.ENGINE_EXCEPTION, r.error().code);
    }

    public void testOutOfMemoryInEngineIsContained() {
        FaultPlan f = new FaultPlan();
        f.outOfMemoryOnLaunch = true;
        EngineResult<String> r = sup(new FakeEngine(EngineState.READY, f), new FakeClock()).launch(req());
        assertEquals(EngineErrorCode.OUT_OF_MEMORY, r.error().code);
    }

    public void testHungEngineTimesOut() {
        FaultPlan f = new FaultPlan();
        f.hangOnLaunch = true;
        long t0 = System.currentTimeMillis();
        EngineResult<String> r = sup(new FakeEngine(EngineState.READY, f), new FakeClock()).launch(req());
        assertEquals(EngineErrorCode.TIMEOUT, r.error().code);
        assertTrue("caller must be released quickly", System.currentTimeMillis() - t0 < 3000);
    }

    public void testInvalidRequestRejectedBeforeEngine() {
        FakeEngine e = new FakeEngine(EngineState.READY, new FaultPlan());
        EngineSupervisor s = sup(e, new FakeClock());
        String[][] bad = {{"C1", "a.exe"}, {"c1", "a.txt"}, {"", "a.exe"}, {"c1", ""}, {"c1;rm", "a.exe"}};
        for (String[] b : bad) {
            EngineResult<String> r = s.launch(new LaunchRequest(b[0], b[1], new ArrayList<String>(), 800, 600));
            assertEquals(EngineErrorCode.INVALID_REQUEST, r.error().code);
        }
        assertEquals(EngineState.READY, e.state());
    }

    public void testCrashLoopDisablesEngineUntilUserReset() {
        FakeClock clock = new FakeClock();
        FaultPlan f = new FaultPlan();
        f.failLaunch = true;
        FakeEngine e = new FakeEngine(EngineState.READY, f);
        EngineSupervisor s = sup(e, clock);
        for (int i = 0; i < 3; i++) {
            EngineResult<String> r = s.launch(req());
            assertEquals(EngineErrorCode.START_FAILED, r.error().code);
            clock.now += 120000;      // far beyond backoff, still inside the crash window
            e.verify();               // FAILED -> READY, as the desktop would after a retry
        }
        assertTrue(s.isDisabled());
        assertEquals(EngineErrorCode.DISABLED, s.launch(req()).error().code);
        s.userReset();
        assertFalse(s.isDisabled());
    }

    public void testBackoffGrowsAndExpires() {
        FakeClock clock = new FakeClock();
        FaultPlan f = new FaultPlan();
        f.failLaunch = true;
        FakeEngine e = new FakeEngine(EngineState.READY, f);
        EngineSupervisor s = sup(e, clock);
        s.launch(req());
        assertEquals(1000, s.backoffRemainingMs());
        e.verify();
        assertEquals(EngineErrorCode.BACKOFF, s.launch(req()).error().code);
        clock.now += 1001;
        assertEquals(0, s.backoffRemainingMs());
    }

    public void testOldCrashesFallOutOfWindow() {
        FakeClock clock = new FakeClock();
        FaultPlan f = new FaultPlan();
        f.failLaunch = true;
        FakeEngine e = new FakeEngine(EngineState.READY, f);
        EngineSupervisor s = sup(e, clock);
        s.launch(req());
        clock.now += 700000;   // > 10 minute window
        assertEquals(0, s.crashesInWindow());
        assertFalse(s.isDisabled());
    }

    public void testAsynchronousProcessDeathIsRecorded() {
        FakeClock clock = new FakeClock();
        FakeEngine e = new FakeEngine(EngineState.READY, new FaultPlan());
        EngineSupervisor s = sup(e, clock);
        String sid = s.launch(req()).value();
        e.simulateProcessDeath(sid, 139, "SIGSEGV");
        assertEquals(1, s.crashesInWindow());
        assertEquals(EngineErrorCode.PROCESS_DIED, s.lastError().code);
        assertEquals(Integer.valueOf(139), s.lastError().exitCode);
    }

    public void testLaunchBeforeReadyIsRefused() {
        EngineResult<String> r = sup(new FakeEngine(EngineState.NOT_INSTALLED, new FaultPlan()), new FakeClock()).launch(req());
        assertEquals(EngineErrorCode.NOT_READY, r.error().code);
    }
}
