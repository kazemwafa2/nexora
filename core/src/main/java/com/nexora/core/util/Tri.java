package com.nexora.core.util;

/** Three-valued answer: a fact that was not measured is UNKNOWN, never silently YES or NO. */
public enum Tri {
    YES, NO, UNKNOWN;

    public static Tri of(boolean b) { return b ? YES : NO; }
}
