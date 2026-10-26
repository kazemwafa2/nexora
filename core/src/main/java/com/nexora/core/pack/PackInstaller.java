package com.nexora.core.pack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs an engine pack from a local folder (USB/SD/download) with these guarantees:
 * checks space first, copies to a private staging folder, verifies every hash, then renames
 * atomically. An existing install is never overwritten or deleted; only the staging folder that
 * THIS call created may be removed on failure.
 */
public final class PackInstaller {
    private final SpaceChecker space;

    public PackInstaller(SpaceChecker space) { this.space = space; }

    public InstallResult install(File sourceDir, File installRoot, List<String> deviceAbis,
                                 ProgressListener progress, CancelToken cancel) {
        List<String> none = new ArrayList<String>();
        File mf = new File(sourceDir, "manifest.json");
        PackManifest m;
        try {
            m = PackManifest.parse(readAll(mf));
        } catch (PackManifest.Invalid e) {
            return new InstallResult(InstallResult.Code.BAD_MANIFEST, e.getMessage(), null, none);
        } catch (IOException e) {
            return new InstallResult(InstallResult.Code.IO_ERROR, "cannot read manifest.json: " + e.getMessage(), null, none);
        }
        if (!deviceAbis.contains(m.abi)) {
            return new InstallResult(InstallResult.Code.ABI_MISMATCH,
                    "pack ABI " + m.abi + " is not supported by this device", null, none);
        }
        File finalDir = new File(installRoot, m.packId + "-" + m.version);
        if (finalDir.exists()) {
            return new InstallResult(InstallResult.Code.ALREADY_INSTALLED, finalDir.getName() + " already exists", finalDir, none);
        }
        if (!installRoot.isDirectory() && !installRoot.mkdirs()) {
            return new InstallResult(InstallResult.Code.IO_ERROR, "cannot create install root", null, none);
        }
        long need = m.totalSize + m.totalSize / 10 + 1024 * 1024;
        long free = space.freeBytes(installRoot);
        if (free >= 0 && free < need) {
            return new InstallResult(InstallResult.Code.NO_SPACE, "need " + need + " bytes, free " + free, null, none);
        }
        File staging = new File(installRoot, "." + finalDir.getName() + ".staging");
        if (staging.exists()) {
            return new InstallResult(InstallResult.Code.IO_ERROR, "stale staging folder exists: " + staging.getName(), null, none);
        }
        if (!staging.mkdirs()) {
            return new InstallResult(InstallResult.Code.IO_ERROR, "cannot create staging folder", null, none);
        }
        try {
            long done = 0;
            for (PackFile pf : m.files) {
                if (cancel != null && cancel.isCancelled()) {
                    removeOwn(staging);
                    return new InstallResult(InstallResult.Code.CANCELLED, "cancelled by user", null, none);
                }
                File src = new File(sourceDir, pf.path);
                File dst = new File(staging, pf.path);
                if (!dst.getCanonicalPath().startsWith(staging.getCanonicalPath() + File.separator)) {
                    removeOwn(staging);
                    return new InstallResult(InstallResult.Code.BAD_MANIFEST, "path escapes install folder: " + pf.path, null, none);
                }
                File parent = dst.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + parent);
                copy(src, dst);
                done += pf.size;
                if (progress != null) progress.onProgress(done, m.totalSize, pf.path);
            }
            List<String> problems = PackVerifier.verify(staging, m, cancel);
            if (!problems.isEmpty()) {
                removeOwn(staging);
                boolean cancelled = problems.size() == 1 && "cancelled".equals(problems.get(0));
                return new InstallResult(cancelled ? InstallResult.Code.CANCELLED : InstallResult.Code.CORRUPT,
                        cancelled ? "cancelled by user" : "verification failed", null, problems);
            }
            if (!staging.renameTo(finalDir)) {
                removeOwn(staging);
                return new InstallResult(InstallResult.Code.IO_ERROR, "could not move staging folder into place", null, none);
            }
            return new InstallResult(InstallResult.Code.OK, "installed", finalDir, none);
        } catch (IOException e) {
            removeOwn(staging);
            return new InstallResult(InstallResult.Code.IO_ERROR, e.getMessage(), null, none);
        }
    }

    private static String readAll(File f) throws IOException {
        if (f.length() > 16L * 1024 * 1024) throw new IOException("manifest too large");
        InputStream in = new FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    private static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    /** Deletes only the staging folder created by this installer. */
    private static void removeOwn(File dir) {
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (File k : kids) {
                if (k.isDirectory()) removeOwn(k); else k.delete();
            }
        }
        dir.delete();
    }
}
