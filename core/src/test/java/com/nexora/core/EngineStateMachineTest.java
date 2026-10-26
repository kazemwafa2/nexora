package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.engine.EngineState;
import com.nexora.core.engine.EngineStateMachine;

public class EngineStateMachineTest {
    public void testHappyLifecycle() {
        EngineStateMachine sm = new EngineStateMachine(EngineState.NOT_INSTALLED);
        EngineState[] path = {EngineState.INSTALLING, EngineState.INSTALLED, EngineState.VERIFYING, EngineState.READY,
            EngineState.STARTING, EngineState.RUNNING, EngineState.STOPPING, EngineState.READY};
        for (EngineState s : path) sm.transition(s);
        assertEquals(EngineState.READY, sm.current());
    }

    public void testIllegalTransitionsThrowAndKeepState() {
        final EngineStateMachine sm = new EngineStateMachine(EngineState.NOT_INSTALLED);
        assertThrows(IllegalStateException.class, () -> sm.transition(EngineState.RUNNING));
        assertThrows(IllegalStateException.class, () -> sm.transition(EngineState.READY));
        assertEquals(EngineState.NOT_INSTALLED, sm.current());
    }

    public void testFailedCanRecoverOnlyThroughVerifyOrReinstall() {
        assertTrue(EngineStateMachine.isLegal(EngineState.FAILED, EngineState.VERIFYING));
        assertTrue(EngineStateMachine.isLegal(EngineState.FAILED, EngineState.INSTALLING));
        assertFalse(EngineStateMachine.isLegal(EngineState.FAILED, EngineState.RUNNING));
        assertFalse(EngineStateMachine.isLegal(EngineState.FAILED, EngineState.READY));
    }

    public void testDisabledNeedsExplicitReset() {
        assertFalse(EngineStateMachine.isLegal(EngineState.DISABLED, EngineState.STARTING));
        assertTrue(EngineStateMachine.isLegal(EngineState.DISABLED, EngineState.VERIFYING));
    }

    public void testEveryStateHasNoSelfLoopExceptExplicit() {
        for (EngineState s : EngineState.values()) assertFalse(EngineStateMachine.isLegal(s, s));
    }
}
