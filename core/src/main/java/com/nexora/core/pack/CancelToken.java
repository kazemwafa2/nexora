package com.nexora.core.pack;

public final class CancelToken {
    private volatile boolean cancelled;

    public void cancel() { cancelled = true; }

    public boolean isCancelled() { return cancelled; }
}
