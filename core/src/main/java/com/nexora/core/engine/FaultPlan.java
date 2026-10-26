package com.nexora.core.engine;

/** Fault injection switches for contract and resilience tests. */
public final class FaultPlan {
    public boolean failVerify;
    public boolean failLaunch;
    public boolean throwOnLaunch;
    public boolean hangOnLaunch;
    public boolean outOfMemoryOnLaunch;
    public long launchDelayMs;
}
