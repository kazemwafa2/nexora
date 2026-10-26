package com.nexora.core.engine;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Only these transitions are legal; anything else is a programming error and throws. */
public final class EngineStateMachine {
    private static final Map<EngineState, Set<EngineState>> ALLOWED = new EnumMap<EngineState, Set<EngineState>>(EngineState.class);

    static {
        allow(EngineState.NOT_INSTALLED, EngineState.INSTALLING);
        allow(EngineState.INSTALLING, EngineState.INSTALLED, EngineState.FAILED, EngineState.NOT_INSTALLED);
        allow(EngineState.INSTALLED, EngineState.VERIFYING, EngineState.NOT_INSTALLED);
        allow(EngineState.VERIFYING, EngineState.READY, EngineState.FAILED);
        allow(EngineState.READY, EngineState.STARTING, EngineState.VERIFYING, EngineState.NOT_INSTALLED);
        allow(EngineState.STARTING, EngineState.RUNNING, EngineState.FAILED);
        allow(EngineState.RUNNING, EngineState.STOPPING, EngineState.FAILED, EngineState.READY);
        allow(EngineState.STOPPING, EngineState.READY, EngineState.FAILED);
        allow(EngineState.FAILED, EngineState.VERIFYING, EngineState.INSTALLING, EngineState.NOT_INSTALLED, EngineState.DISABLED);
        allow(EngineState.DISABLED, EngineState.VERIFYING, EngineState.NOT_INSTALLED);
    }

    private static void allow(EngineState from, EngineState... to) {
        Set<EngineState> s = EnumSet.noneOf(EngineState.class);
        for (EngineState t : to) s.add(t);
        ALLOWED.put(from, s);
    }

    private EngineState current;

    public EngineStateMachine(EngineState initial) { this.current = initial; }

    public synchronized EngineState current() { return current; }

    public static boolean isLegal(EngineState from, EngineState to) {
        Set<EngineState> s = ALLOWED.get(from);
        return s != null && s.contains(to);
    }

    /** Returns the previous state. */
    public synchronized EngineState transition(EngineState to) {
        if (!isLegal(current, to)) {
            throw new IllegalStateException("illegal engine transition " + current + " -> " + to);
        }
        EngineState old = current;
        current = to;
        return old;
    }
}
