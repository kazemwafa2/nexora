package com.nexora.core.engine;

public final class EngineError {
    public final EngineErrorCode code;
    public final String detail;
    public final Integer exitCode;   // null if unknown
    public final String signal;      // null if none/unknown

    public EngineError(EngineErrorCode code, String detail, Integer exitCode, String signal) {
        this.code = code;
        this.detail = detail == null ? "" : detail;
        this.exitCode = exitCode;
        this.signal = signal;
    }

    public static EngineError of(EngineErrorCode code, String detail) {
        return new EngineError(code, detail, null, null);
    }

    @Override public String toString() {
        return code + (detail.length() > 0 ? " (" + detail + ")" : "")
                + (exitCode != null ? " exit=" + exitCode : "") + (signal != null ? " signal=" + signal : "");
    }
}
