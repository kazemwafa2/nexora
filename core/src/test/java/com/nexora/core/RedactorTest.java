package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.diag.Redactor;

public class RedactorTest {
    public void testRemovesIdentifiers() {
        String s = Redactor.redact("mail me@example.com imei 356938035643809 mac AA:BB:CC:DD:EE:FF ip 192.168.1.20 /storage/emulated/0/Download/x");
        assertFalse(s.contains("me@example.com"));
        assertFalse(s.contains("356938035643809"));
        assertFalse(s.contains("AA:BB:CC:DD:EE:FF"));
        assertFalse(s.contains("192.168.1.20"));
        assertTrue(s.contains("/storage/emulated/<user>"));
    }

    public void testKeepsOrdinaryText() {
        assertEquals("exit 139 (SIGSEGV)", Redactor.redact("exit 139 (SIGSEGV)"));
        assertNull(Redactor.redact(null));
    }
}
