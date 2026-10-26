package com.nexora.core.engine;

/**
 * Contract between the desktop and ANY Windows-compatibility engine. The desktop never imports an
 * engine implementation; it only sees this interface (on Android it is bridged by a bound service
 * in a separate process). Implementations must be safe to call from one worker thread.
 *
 * Error reporting rule: a call that fails reports the error ONLY through its return value.
 * EngineListener.onError is reserved for ASYNCHRONOUS failures (e.g. the program dies later), so the
 * supervisor never counts one failure twice.
 */
public interface IEngine {
    int CONTRACT_VERSION = 1;

    EngineState state();

    /** Re-checks the installed engine pack (hashes, ABI). */
    EngineResult<Void> verify();

    /** Starts a Windows program; returns a session id. */
    EngineResult<String> launch(LaunchRequest request);

    EngineResult<Void> stop(String sessionId, boolean force);

    void addListener(EngineListener l);

    void removeListener(EngineListener l);

    /** Releases everything; must not throw. */
    void shutdown();
}
