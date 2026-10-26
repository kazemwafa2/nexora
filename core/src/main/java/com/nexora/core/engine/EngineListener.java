package com.nexora.core.engine;

public interface EngineListener {
    void onStateChanged(EngineState oldState, EngineState newState);

    void onError(EngineError error);

    void onSessionExit(String sessionId, int exitCode, String signal);
}
