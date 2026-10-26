package com.nexora.testing;

public final class Assert {
    private Assert() { }

    public interface Thrower { void run() throws Throwable; }

    public static void assertTrue(boolean c, String msg) { if (!c) throw new AssertionError(msg); }

    public static void assertTrue(boolean c) { assertTrue(c, "expected true"); }

    public static void assertFalse(boolean c) { assertTrue(!c, "expected false"); }

    public static void assertTrue(String msg, boolean c) { assertTrue(c, msg); }

    public static void assertFalse(String msg, boolean c) { assertTrue(!c, msg); }

    public static void assertEquals(Object exp, Object act) {
        if (exp == null ? act != null : !exp.equals(act)) throw new AssertionError("expected <" + exp + "> but was <" + act + ">");
    }

    public static void assertEquals(long exp, long act) {
        if (exp != act) throw new AssertionError("expected <" + exp + "> but was <" + act + ">");
    }

    public static void assertNull(Object o) { if (o != null) throw new AssertionError("expected null but was <" + o + ">"); }

    public static void assertNotNull(Object o) { if (o == null) throw new AssertionError("expected non-null"); }

    public static <T extends Throwable> T assertThrows(Class<T> type, Thrower t) {
        try {
            t.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) return type.cast(e);
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + e);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }
}
