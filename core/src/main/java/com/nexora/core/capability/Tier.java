package com.nexora.core.capability;

public enum Tier {
    BASIC, STANDARD, ADVANCED;

    public boolean atLeast(Tier other) { return ordinal() >= other.ordinal(); }

    public static Tier lower(Tier a, Tier b) { return a.ordinal() <= b.ordinal() ? a : b; }
}
