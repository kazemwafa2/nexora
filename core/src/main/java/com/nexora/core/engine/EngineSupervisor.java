package com.nexora.core.engine;

import com.nexora.core.util.Clock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Keeps the desktop alive no matter what the engine does. Every call returns an EngineResult
 * (never throws), engine exceptions/OOM/hangs become errors, and a crash loop disables the engine
 * until the user resets it.
 */
public final class EngineSupervisor implements EngineListener {
    private final IEngine engine;
    private final Clock clock;
    private final int maxCrashesInWindow;
    private final long windowMs;
    private final long backoffBaseMs;
    private final long backoffMaxMs;
    private final long callTimeoutMs;

    private final List<Long> crashTimes = new ArrayList<Long>();
    private EngineError lastError;
    private boolean disabled;

    public EngineSupervisor(IEngine engine, Clock clock, int maxCrashesInWindow, long windowMs,
                            long backoffBaseMs, long backoffMaxMs, long callTimeoutMs) {
        this.engine = engine;
        this.clock = clock;
        this.maxCrashesInWindow = maxCrashesInWindow;
        this.windowMs = windowMs;
        this.backoffBaseMs = backoffBaseMs;
        this.backoffMaxMs = backoffMaxMs;
        this.callTimeoutMs = callTimeoutMs;
        engine.addListener(this);
    }

    public synchronized boolean isDisabled() { return disabled; }

    public synchronized EngineError lastError() { return lastError; }

    public synchronized int crashesInWindow() { prune(); return crashTimes.size(); }

    /** ms until a new start is allowed (0 = allowed now). */
    public synchronized long backoffRemainingMs() {
        prune();
        if (crashTimes.isEmpty()) return 0;
        long last = crashTimes.get(crashTimes.size() - 1).longValue();
        long delay = backoffBaseMs;
        for (int i = 1; i < crashTimes.size() && delay < backoffMaxMs; i++) delay *= 2;
        if (delay > backoffMaxMs) delay = backoffMaxMs;
        long rem = last + delay - clock.nowMs();
        return rem > 0 ? rem : 0;
    }

    public synchronized void userReset() {
        crashTimes.clear();
        disabled = false;
        lastError = null;
    }

    public EngineResult<String> launch(final LaunchRequest req) {
        String bad = req == null ? "null request" : req.validate();
        if (bad != null) return fail(EngineError.of(EngineErrorCode.INVALID_REQUEST, bad));
        synchronized (this) {
            if (disabled) {
                return EngineResult.err(EngineError.of(EngineErrorCode.DISABLED, "engine disabled after repeated crashes"));
            }
            long rem = backoffRemainingMs();
            if (rem > 0) {
                return EngineResult.err(EngineError.of(EngineErrorCode.BACKOFF, "retry in " + rem + " ms"));
            }
        }
        return guarded(new Callable<EngineResult<String>>() {
            @Override public EngineResult<String> call() { return engine.launch(req); }
        });
    }

    public EngineResult<Void> verify() {
        return guarded(new Callable<EngineResult<Void>>() {
            @Override public EngineResult<Void> call() { return engine.verify(); }
        });
    }

    public EngineResult<Void> stop(final String sessionId, final boolean force) {
        return guarded(new Callable<EngineResult<Void>>() {
            @Override public EngineResult<Void> call() { return engine.stop(sessionId, force); }
        });
    }

    private <T> EngineResult<T> guarded(Callable<EngineResult<T>> call) {
        FutureTask<EngineResult<T>> task = new FutureTask<EngineResult<T>>(call);
        Thread th = new Thread(task, "engine-call");
        th.setDaemon(true);
        th.start();
        try {
            EngineResult<T> r = task.get(callTimeoutMs, TimeUnit.MILLISECONDS);
            if (r == null) return fail(EngineError.of(EngineErrorCode.ENGINE_EXCEPTION, "engine returned null"));
            if (!r.isOk()) noteFailure(r.error());
            return r;
        } catch (TimeoutException e) {
            task.cancel(true);
            th.interrupt();
            safeShutdown();
            return fail(EngineError.of(EngineErrorCode.TIMEOUT, "engine call timed out after " + callTimeoutMs + " ms"));
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof OutOfMemoryError) {
                return fail(EngineError.of(EngineErrorCode.OUT_OF_MEMORY, "engine ran out of memory"));
            }
            return fail(EngineError.of(EngineErrorCode.ENGINE_EXCEPTION,
                    (c == null ? "unknown" : c.getClass().getSimpleName()) + ": " + (c == null ? "" : c.getMessage())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(EngineError.of(EngineErrorCode.TIMEOUT, "interrupted"));
        }
    }

    private void safeShutdown() {
        try {
            engine.shutdown();
        } catch (Throwable ignored) {
            // shutdown must never take the desktop down
        }
    }

    private <T> EngineResult<T> fail(EngineError e) {
        noteFailure(e);
        return EngineResult.err(e);
    }

    private synchronized void noteFailure(EngineError e) {
        lastError = e;
        if (isCrashLike(e.code)) {
            crashTimes.add(Long.valueOf(clock.nowMs()));
            prune();
            if (crashTimes.size() >= maxCrashesInWindow) disabled = true;
        }
    }

    private static boolean isCrashLike(EngineErrorCode c) {
        return c == EngineErrorCode.START_FAILED || c == EngineErrorCode.PROCESS_DIED || c == EngineErrorCode.TIMEOUT
                || c == EngineErrorCode.OUT_OF_MEMORY || c == EngineErrorCode.ENGINE_EXCEPTION;
    }

    private void prune() {
        long cutoff = clock.nowMs() - windowMs;
        while (!crashTimes.isEmpty() && crashTimes.get(0).longValue() < cutoff) crashTimes.remove(0);
    }

    // ---- EngineListener: asynchronous failures reported by the engine itself
    @Override public void onStateChanged(EngineState o, EngineState n) { }

    @Override public void onError(EngineError error) { noteFailure(error); }

    @Override public void onSessionExit(String id, int exit, String sig) { }
}
