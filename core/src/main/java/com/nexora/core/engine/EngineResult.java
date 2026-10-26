package com.nexora.core.engine;

/** Calls into the engine never throw to the UI; they return this. */
public final class EngineResult<T> {
    private final T value;
    private final EngineError error;

    private EngineResult(T value, EngineError error) {
        this.value = value;
        this.error = error;
    }

    public static <T> EngineResult<T> ok(T value) { return new EngineResult<T>(value, null); }

    public static <T> EngineResult<T> err(EngineError e) { return new EngineResult<T>(null, e); }

    public boolean isOk() { return error == null; }

    public T value() { return value; }

    public EngineError error() { return error; }
}
