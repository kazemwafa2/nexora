package com.nexora.testing;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Tiny dependency-free test runner: runs every public no-arg method named test* on each class. */
public final class TestRunner {
    private TestRunner() { }

    public static void main(String[] classNames) throws Exception {
        int pass = 0, fail = 0;
        List<String> failures = new ArrayList<String>();
        for (String cn : classNames) {
            Class<?> c = Class.forName(cn);
            List<Method> ms = new ArrayList<Method>(Arrays.asList(c.getMethods()));
            Collections.sort(ms, new java.util.Comparator<Method>() {
                @Override public int compare(Method a, Method b) { return a.getName().compareTo(b.getName()); }
            });
            for (Method m : ms) {
                if (!m.getName().startsWith("test") || m.getParameterTypes().length != 0) continue;
                String label = c.getSimpleName() + "." + m.getName();
                try {
                    Object inst = c.getDeclaredConstructor().newInstance();
                    m.invoke(inst);
                    pass++;
                    System.out.println("PASS  " + label);
                } catch (InvocationTargetException e) {
                    fail++;
                    Throwable t = e.getCause();
                    failures.add(label + ": " + t);
                    System.out.println("FAIL  " + label + "  -> " + t);
                    StackTraceElement[] st = t.getStackTrace();
                    for (int i = 0; i < Math.min(4, st.length); i++) System.out.println("        at " + st[i]);
                }
            }
        }
        System.out.println();
        System.out.println("TESTS: " + (pass + fail) + "  PASS: " + pass + "  FAIL: " + fail);
        if (fail > 0) {
            for (String f : failures) System.out.println("  FAILED: " + f);
            System.exit(1);
        }
        System.exit(0);
    }
}
