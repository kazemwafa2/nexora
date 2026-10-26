package com.nexora.core.diag;

import java.util.List;

/** One automated diagnostic. Implementations must not modify user files. */
public interface DiagTest {
    String id();

    String name();

    /** IDs of tests that must have PASSED, otherwise this test is reported UNKNOWN (not run). */
    List<String> requires();

    /** Maximum run time in ms, or 0 to use the runner default. */
    long timeoutMs();

    TestResult run() throws Exception;
}
