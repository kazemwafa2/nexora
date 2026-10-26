package com.nexora.core.engine;

/** Stable error taxonomy. The UI maps each code to a localized message key "engine.error.<NAME>". */
public enum EngineErrorCode {
    NOT_INSTALLED(true), NOT_READY(true), PACK_CORRUPT(true), ABI_UNSUPPORTED(false), NO_SPACE(true),
    START_FAILED(true), PROCESS_DIED(true), TIMEOUT(true), OUT_OF_MEMORY(true), PERMISSION_DENIED(true),
    INVALID_REQUEST(true), ENGINE_EXCEPTION(true), BACKOFF(true), DISABLED(true), UNKNOWN(true);

    public final boolean recoverable;

    EngineErrorCode(boolean recoverable) { this.recoverable = recoverable; }

    public String messageKey() { return "engine.error." + name(); }
}
