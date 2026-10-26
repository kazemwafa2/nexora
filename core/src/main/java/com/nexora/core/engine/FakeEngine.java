package com.nexora.core.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** In-memory engine used to test the desktop side without any real Wine/Box64. Not a real engine. */
public final class FakeEngine implements IEngine {
    private final EngineStateMachine sm;
    private final FaultPlan faults;
    private final List<EngineListener> listeners = new ArrayList<EngineListener>();
    private final Set<String> sessions = new HashSet<String>();
    private int counter;

    public FakeEngine(EngineState initial, FaultPlan faults) {
        this.sm = new EngineStateMachine(initial);
        this.faults = faults;
    }

    @Override public EngineState state() { return sm.current(); }

    @Override public EngineResult<Void> verify() {
        EngineState cur = sm.current();
        if (cur == EngineState.NOT_INSTALLED || cur == EngineState.INSTALLING) {
            return EngineResult.err(EngineError.of(EngineErrorCode.NOT_INSTALLED, "engine pack is not installed"));
        }
        if (cur == EngineState.INSTALLED || cur == EngineState.READY || cur == EngineState.FAILED || cur == EngineState.DISABLED) {
            move(EngineState.VERIFYING);
        } else {
            return EngineResult.err(EngineError.of(EngineErrorCode.NOT_READY, "cannot verify in state " + cur));
        }
        if (faults.failVerify) {
            move(EngineState.FAILED);
            return EngineResult.err(EngineError.of(EngineErrorCode.PACK_CORRUPT, "injected verify failure"));
        }
        move(EngineState.READY);
        return EngineResult.ok(null);
    }

    @Override public EngineResult<String> launch(LaunchRequest req) {
        String bad = req.validate();
        if (bad != null) return EngineResult.err(EngineError.of(EngineErrorCode.INVALID_REQUEST, bad));
        if (sm.current() != EngineState.READY) {
            return EngineResult.err(EngineError.of(EngineErrorCode.NOT_READY, "state is " + sm.current()));
        }
        if (faults.throwOnLaunch) throw new IllegalStateException("injected exception");
        if (faults.outOfMemoryOnLaunch) throw new OutOfMemoryError("injected OOM");
        if (faults.hangOnLaunch) {
            try {
                Thread.sleep(60000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return EngineResult.err(EngineError.of(EngineErrorCode.TIMEOUT, "interrupted while hung"));
            }
        }
        if (faults.launchDelayMs > 0) {
            try {
                Thread.sleep(faults.launchDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        move(EngineState.STARTING);
        if (faults.failLaunch) {
            move(EngineState.FAILED);
            return EngineResult.err(new EngineError(EngineErrorCode.START_FAILED, "injected start failure", Integer.valueOf(139), "SIGSEGV"));
        }
        move(EngineState.RUNNING);
        String id = "s" + (++counter);
        sessions.add(id);
        return EngineResult.ok(id);
    }

    @Override public EngineResult<Void> stop(String sessionId, boolean force) {
        if (!sessions.remove(sessionId)) {
            return EngineResult.err(EngineError.of(EngineErrorCode.INVALID_REQUEST, "unknown session"));
        }
        move(EngineState.STOPPING);
        move(EngineState.READY);
        return EngineResult.ok(null);
    }

    /** Test helper: the running program dies by itself. */
    public void simulateProcessDeath(String sessionId, int exitCode, String signal) {
        sessions.remove(sessionId);
        move(EngineState.FAILED);
        EngineError e = new EngineError(EngineErrorCode.PROCESS_DIED, "process died", Integer.valueOf(exitCode), signal);
        fireError(e);
        for (EngineListener l : snapshot()) l.onSessionExit(sessionId, exitCode, signal);
    }

    @Override public void addListener(EngineListener l) { synchronized (listeners) { listeners.add(l); } }

    @Override public void removeListener(EngineListener l) { synchronized (listeners) { listeners.remove(l); } }

    @Override public void shutdown() { sessions.clear(); }

    private void move(EngineState to) {
        EngineState old = sm.transition(to);
        for (EngineListener l : snapshot()) l.onStateChanged(old, to);
    }

    private void fireError(EngineError e) {
        for (EngineListener l : snapshot()) l.onError(e);
    }

    private List<EngineListener> snapshot() {
        synchronized (listeners) { return new ArrayList<EngineListener>(listeners); }
    }
}
