package com.nexora.desktop;

import android.content.Context;

import com.nexora.core.diag.DiagTest;
import com.nexora.core.diag.TestResult;
import com.nexora.core.util.Sha256;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** On-device diagnostics run from the Diagnostics window. They only touch app-private storage. */
final class AndroidDiagTests {
    static final String CAPABILITY = "ANDROID.CAPABILITY";
    static final String STORAGE = "ANDROID.STORAGE_RW";
    static final String EXEC = "ANDROID.EXEC_PRIVATE";

    private AndroidDiagTests() { }

    private abstract static class Base implements DiagTest {
        private final String id;
        private final String name;
        private final long timeout;

        Base(String id, String name, long timeout) {
            this.id = id;
            this.name = name;
            this.timeout = timeout;
        }

        @Override public String id() { return id; }

        @Override public String name() { return name; }

        @Override public List<String> requires() { return Collections.emptyList(); }

        @Override public long timeoutMs() { return timeout; }
    }

    static List<DiagTest> create(final Context ctx) {
        List<DiagTest> l = new ArrayList<DiagTest>();
        l.add(new Base(CAPABILITY, "Device facts can be measured", 5000) {
            @Override public TestResult run() {
                com.nexora.core.capability.DeviceFacts f = CapabilityProbe.collect(ctx, com.nexora.core.util.Tri.UNKNOWN);
                Map<String, String> ev = new LinkedHashMap<String, String>();
                ev.put("sdk", String.valueOf(f.sdkInt));
                ev.put("abis", f.abis.toString());
                ev.put("total_ram_kb", String.valueOf(f.totalRamKb));
                ev.put("free_storage_kb", String.valueOf(f.freeStorageKb));
                ev.put("cores", String.valueOf(f.cores));
                boolean enough = f.sdkInt > 0 && !f.abis.isEmpty() && f.totalRamKb > 0;
                TestResult r = enough ? TestResult.pass(CAPABILITY, name(), "core facts measured")
                        : TestResult.fail(CAPABILITY, name(), "some core facts could not be measured");
                return r.withEvidence(ev);
            }
        });
        l.add(new Base(STORAGE, "App-private storage write, read back and hash", 10000) {
            @Override public TestResult run() throws Exception {
                File dir = new File(ctx.getFilesDir(), "diag");
                if (!dir.isDirectory() && !dir.mkdirs()) return TestResult.fail(STORAGE, name(), "cannot create diag folder");
                File f = new File(dir, "selftest.bin");
                byte[] data = new byte[65536];
                for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 7 + 3);
                try {
                    OutputStream o = new FileOutputStream(f);
                    try {
                        o.write(data);
                    } finally {
                        o.close();
                    }
                    String h = Sha256.ofFile(f);
                    boolean same = h.equals(Sha256.ofBytes(data));
                    return same ? TestResult.pass(STORAGE, name(), "64 KiB written and verified")
                            : TestResult.fail(STORAGE, name(), "hash mismatch after write");
                } finally {
                    f.delete();   // only the file this test created
                }
            }
        });
        l.add(new Base(EXEC, "Run a copy of a system binary from app-private storage", 10000) {
            @Override public TestResult run() throws Exception {
                File src = new File("/system/bin/true");
                if (!src.exists()) return TestResult.unknown(EXEC, name(), "reference binary /system/bin/true not found");
                File dir = new File(ctx.getFilesDir(), "diag");
                if (!dir.isDirectory() && !dir.mkdirs()) return TestResult.fail(EXEC, name(), "cannot create diag folder");
                File dst = new File(dir, "true");
                try {
                    copy(src, dst);
                    if (!dst.setExecutable(true, true)) return TestResult.fail(EXEC, name(), "cannot set executable bit");
                    Process p = new ProcessBuilder(dst.getAbsolutePath()).redirectErrorStream(true).start();
                    int rc = p.waitFor();
                    return rc == 0 ? TestResult.pass(EXEC, name(), "exec from private storage works with this app's targetSdk")
                            : TestResult.fail(EXEC, name(), "exit code " + rc);
                } catch (IOException e) {
                    return TestResult.fail(EXEC, name(), "exec refused: " + e.getMessage());
                } finally {
                    dst.delete();   // only the copy this test created
                }
            }
        });
        String[][] engine = {
            {"ENGINE.BOX_START", "Box64 starts"}, {"ENGINE.X64_RUN", "x86_64 test program runs"},
            {"ENGINE.WINE_SMOKE", "Wine starts and runs a console command"}, {"ENGINE.GRAPHICS_SMOKE", "Graphics backend smoke test"},
            {"ENGINE.STABILITY", "Repeated runs are stable"}};
        for (final String[] e : engine) {
            l.add(new Base(e[0], e[1], 1000) {
                @Override public TestResult run() {
                    return TestResult.unknown(id(), name(), "not implemented in this build (engine not installed)");
                }
            });
        }
        return l;
    }

    private static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }
}
