package com.nexora.core.util;

/** Injectable time source so time-dependent logic can be tested without sleeping. */
public interface Clock {
    long nowMs();

    Clock SYSTEM = new Clock() {
        @Override public long nowMs() { return System.currentTimeMillis(); }
    };
}
