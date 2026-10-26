package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.pack.*;
import com.nexora.core.util.Sha256;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;

public class PackInstallerTest {
    static final List<String> ABIS = Arrays.asList("arm64-v8a", "armeabi-v7a");

    static File tmp() throws Exception {
        File d = File.createTempFile("nxpack", "");
        d.delete();
        d.mkdirs();
        return d;
    }

    static void write(File f, byte[] data) throws Exception {
        f.getParentFile().mkdirs();
        FileOutputStream o = new FileOutputStream(f);
        o.write(data);
        o.close();
    }

    /** Builds a valid source pack with two files and returns its folder. */
    static File makePack(File root, String version, byte[] a, byte[] b) throws Exception {
        File src = new File(root, "src-" + version);
        write(new File(src, "lib/a.bin"), a);
        write(new File(src, "bin/b.bin"), b);
        String json = "{\"pack_id\":\"engine-core\",\"version\":\"" + version + "\",\"abi\":\"arm64-v8a\",\"files\":["
                + "{\"path\":\"lib/a.bin\",\"sha256\":\"" + Sha256.ofBytes(a) + "\",\"size\":" + a.length + "},"
                + "{\"path\":\"bin/b.bin\",\"sha256\":\"" + Sha256.ofBytes(b) + "\",\"size\":" + b.length + "}]}";
        write(new File(src, "manifest.json"), json.getBytes("UTF-8"));
        return src;
    }

    public void testInstallOk() throws Exception {
        File root = tmp();
        File src = makePack(root, "1.0", new byte[1000], new byte[5000]);
        File dest = new File(root, "installed");
        final long[] last = new long[1];
        InstallResult r = new PackInstaller(SpaceChecker.REAL).install(src, dest, ABIS, new ProgressListener() {
            public void onProgress(long d, long t, String f) { last[0] = d; }
        }, new CancelToken());
        assertEquals(InstallResult.Code.OK, r.code);
        assertTrue(new File(r.installedDir, "lib/a.bin").isFile());
        assertEquals(6000, last[0]);
        for (File f : dest.listFiles()) assertFalse("no staging folder may remain: " + f, f.getName().endsWith(".staging"));
    }

    public void testCorruptSourceIsRejectedAndLeavesNothing() throws Exception {
        File root = tmp();
        File src = makePack(root, "1.0", new byte[1000], new byte[5000]);
        byte[] changed = new byte[1000];
        changed[10] = 1;                                    // same size, different content
        write(new File(src, "lib/a.bin"), changed);
        File dest = new File(root, "installed");
        InstallResult r = new PackInstaller(SpaceChecker.REAL).install(src, dest, ABIS, null, null);
        assertEquals(InstallResult.Code.CORRUPT, r.code);
        assertFalse(r.problems.isEmpty());
        assertEquals(0, dest.listFiles().length);
    }

    public void testExistingInstallIsNeverOverwritten() throws Exception {
        File root = tmp();
        File src = makePack(root, "1.0", new byte[100], new byte[200]);
        File dest = new File(root, "installed");
        PackInstaller inst = new PackInstaller(SpaceChecker.REAL);
        assertEquals(InstallResult.Code.OK, inst.install(src, dest, ABIS, null, null).code);
        File marker = new File(dest, "engine-core-1.0/user-note.txt");
        write(marker, "keep me".getBytes("UTF-8"));
        assertEquals(InstallResult.Code.ALREADY_INSTALLED, inst.install(src, dest, ABIS, null, null).code);
        assertTrue(marker.isFile());
    }

    public void testNoSpaceIsCheckedBeforeCopying() throws Exception {
        File root = tmp();
        File src = makePack(root, "1.0", new byte[1000], new byte[1000]);
        File dest = new File(root, "installed");
        InstallResult r = new PackInstaller(new SpaceChecker() {
            public long freeBytes(File d) { return 10; }
        }).install(src, dest, ABIS, null, null);
        assertEquals(InstallResult.Code.NO_SPACE, r.code);
        assertEquals(0, dest.listFiles().length);
    }

    public void testCancelRemovesStaging() throws Exception {
        File root = tmp();
        File src = makePack(root, "1.0", new byte[1000], new byte[1000]);
        File dest = new File(root, "installed");
        final CancelToken tok = new CancelToken();
        InstallResult r = new PackInstaller(SpaceChecker.REAL).install(src, dest, ABIS, new ProgressListener() {
            public void onProgress(long d, long t, String f) { tok.cancel(); }
        }, tok);
        assertEquals(InstallResult.Code.CANCELLED, r.code);
        assertEquals(0, dest.listFiles().length);
    }

    public void testAbiMismatchRejected() throws Exception {
        File root = tmp();
        File src = makePack(root, "1.0", new byte[10], new byte[10]);
        InstallResult r = new PackInstaller(SpaceChecker.REAL).install(src, new File(root, "i"), Arrays.asList("x86_64"), null, null);
        assertEquals(InstallResult.Code.ABI_MISMATCH, r.code);
    }

    public void testBadManifestRejected() throws Exception {
        File root = tmp();
        File src = new File(root, "s");
        write(new File(src, "manifest.json"), "{\"nope\":1}".getBytes("UTF-8"));
        assertEquals(InstallResult.Code.BAD_MANIFEST,
                new PackInstaller(SpaceChecker.REAL).install(src, new File(root, "i"), ABIS, null, null).code);
        assertEquals(InstallResult.Code.IO_ERROR,
                new PackInstaller(SpaceChecker.REAL).install(new File(root, "missing"), new File(root, "i"), ABIS, null, null).code);
    }

    public void testSecondVersionInstallsBesideFirst() throws Exception {
        File root = tmp();
        File dest = new File(root, "installed");
        PackInstaller inst = new PackInstaller(SpaceChecker.REAL);
        assertEquals(InstallResult.Code.OK, inst.install(makePack(root, "1.0", new byte[10], new byte[10]), dest, ABIS, null, null).code);
        assertEquals(InstallResult.Code.OK, inst.install(makePack(root, "1.1", new byte[11], new byte[11]), dest, ABIS, null, null).code);
        assertTrue(new File(dest, "engine-core-1.0").isDirectory());
        assertTrue(new File(dest, "engine-core-1.1").isDirectory());
    }
}
