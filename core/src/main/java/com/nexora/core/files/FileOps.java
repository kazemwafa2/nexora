package com.nexora.core.files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * File-manager operations confined to one root folder. Every operation refuses to leave the root,
 * never overwrites (a copy gets a free name), never follows symbolic links, and never deletes the root.
 * Uses only java.io so it works on Android API 24.
 */
public final class FileOps {
    public enum Code { OK, NOT_FOUND, OUTSIDE_ROOT, IS_ROOT, INTO_ITSELF, SYMLINK_SKIPPED, IO_ERROR, BAD_NAME }

    public static final class Result {
        public final Code code;
        public final File file;
        public final int count;
        public final String detail;

        Result(Code code, File file, int count, String detail) {
            this.code = code;
            this.file = file;
            this.count = count;
            this.detail = detail == null ? "" : detail;
        }

        public boolean isOk() { return code == Code.OK; }
    }

    public static final class Entry {
        public final String name;
        public final boolean directory;
        public final long size;
        public final long modified;

        Entry(String name, boolean directory, long size, long modified) {
            this.name = name;
            this.directory = directory;
            this.size = size;
            this.modified = modified;
        }
    }

    private FileOps() { }

    // ---------------------------------------------------------------- queries

    /** Folders first, then files; case-insensitive name order. Returns an empty list if unreadable. */
    public static List<Entry> list(File dir) {
        List<Entry> out = new ArrayList<Entry>();
        File[] kids = dir.listFiles();
        if (kids == null) return out;
        for (File f : kids) out.add(new Entry(f.getName(), f.isDirectory(), f.isDirectory() ? 0 : f.length(), f.lastModified()));
        Collections.sort(out, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                if (a.directory != b.directory) return a.directory ? -1 : 1;
                int c = a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT));
                return c != 0 ? c : a.name.compareTo(b.name);
            }
        });
        return out;
    }

    /** True if f is the root or lies inside it (by canonical path). */
    public static boolean isInside(File root, File f) {
        try {
            String r = root.getCanonicalPath();
            String c = f.getCanonicalPath();
            return c.equals(r) || c.startsWith(r.endsWith(File.separator) ? r : r + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * True if the directory ENTRY f lies inside root, without resolving f itself: a symbolic link that
     * sits inside root is an entry of root even if it points elsewhere (so it can be removed safely).
     */
    public static boolean entryInside(File root, File f) {
        String n = f.getName();
        if (n.length() == 0 || n.equals(".") || n.equals("..")) return false;
        File p = f.getAbsoluteFile().getParentFile();
        if (p == null) return false;
        return isInside(root, p);
    }

    /** A file is treated as a symbolic link when its canonical path differs from its normalized absolute path. */
    public static boolean isSymlink(File f) {
        try {
            File parent = f.getAbsoluteFile().getParentFile();
            if (parent == null) return false;
            File viaParent = new File(parent.getCanonicalFile(), f.getName());
            return !viaParent.getCanonicalPath().equals(viaParent.getAbsolutePath());
        } catch (IOException e) {
            return true;   // when in doubt, do not follow
        }
    }

    public static boolean validName(String name) {
        if (name == null || name.length() == 0 || name.length() > 200) return false;
        if (name.equals(".") || name.equals("..")) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c == 0 || c < 0x20) return false;
        }
        return true;
    }

    /** "base", "base (2)", "base (3)" ... the first name that does not exist in dir. */
    public static File freeName(File dir, String base) {
        File f = new File(dir, base);
        int n = 2;
        while (f.exists()) {
            f = new File(dir, base + " (" + n + ")");
            n++;
        }
        return f;
    }

    // ---------------------------------------------------------------- operations

    public static Result newFolder(File root, File parent, String baseName) {
        if (!validName(baseName)) return new Result(Code.BAD_NAME, null, 0, baseName);
        if (!parent.isDirectory()) return new Result(Code.NOT_FOUND, null, 0, parent.getName());
        if (!isInside(root, parent)) return new Result(Code.OUTSIDE_ROOT, null, 0, "");
        File f = freeName(parent, baseName);
        return f.mkdir() ? new Result(Code.OK, f, 1, "") : new Result(Code.IO_ERROR, null, 0, "mkdir failed");
    }

    public static Result copy(File root, File src, File destDir) {
        if (!src.exists()) return new Result(Code.NOT_FOUND, null, 0, src.getName());
        if (!destDir.isDirectory()) return new Result(Code.NOT_FOUND, null, 0, destDir.getName());
        if (!entryInside(root, src) || !isInside(root, destDir)) return new Result(Code.OUTSIDE_ROOT, null, 0, "");
        if (isSymlink(src)) return new Result(Code.SYMLINK_SKIPPED, null, 0, src.getName());
        if (src.isDirectory() && isInside(src, destDir)) return new Result(Code.INTO_ITSELF, null, 0, src.getName());
        File target = freeName(destDir, src.getName());
        int[] count = new int[1];
        try {
            copyRec(src, target, count);
        } catch (IOException e) {
            return new Result(Code.IO_ERROR, null, count[0], e.getMessage());
        }
        return new Result(Code.OK, target, count[0], "");
    }

    public static Result move(File root, File src, File destDir) {
        if (!src.exists()) return new Result(Code.NOT_FOUND, null, 0, src.getName());
        if (!destDir.isDirectory()) return new Result(Code.NOT_FOUND, null, 0, destDir.getName());
        if (isSameFile(src, root)) return new Result(Code.IS_ROOT, null, 0, "");
        if (!entryInside(root, src) || !isInside(root, destDir)) return new Result(Code.OUTSIDE_ROOT, null, 0, "");
        if (isSymlink(src)) return new Result(Code.SYMLINK_SKIPPED, null, 0, src.getName());
        if (src.isDirectory() && isInside(src, destDir)) return new Result(Code.INTO_ITSELF, null, 0, src.getName());
        File target = freeName(destDir, src.getName());
        if (src.renameTo(target)) return new Result(Code.OK, target, 1, "");
        Result c = copy(root, src, destDir);
        if (!c.isOk()) return c;
        Result d = delete(root, src);
        return d.isOk() ? new Result(Code.OK, c.file, c.count, "") : d;
    }

    public static Result delete(File root, File target) {
        if (!target.exists() && !isSymlink(target)) return new Result(Code.NOT_FOUND, null, 0, target.getName());
        if (isSameFile(target, root)) return new Result(Code.IS_ROOT, null, 0, "");
        if (!entryInside(root, target)) return new Result(Code.OUTSIDE_ROOT, null, 0, "");
        int[] count = new int[1];
        boolean ok = deleteRec(target, count);
        return ok ? new Result(Code.OK, null, count[0], "") : new Result(Code.IO_ERROR, null, count[0], "could not delete everything");
    }

    // ---------------------------------------------------------------- internals

    private static boolean isSameFile(File a, File b) {
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static void copyRec(File src, File dst, int[] count) throws IOException {
        if (isSymlink(src)) return;   // links inside a copied folder are skipped, never followed
        if (src.isDirectory()) {
            if (!dst.mkdir()) throw new IOException("cannot create " + dst.getName());
            count[0]++;
            File[] kids = src.listFiles();
            if (kids != null) {
                for (File k : kids) copyRec(k, new File(dst, k.getName()), count);
            }
            return;
        }
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
        count[0]++;
    }

    private static boolean deleteRec(File f, int[] count) {
        boolean ok = true;
        if (f.isDirectory() && !isSymlink(f)) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) ok &= deleteRec(k, count);
            }
        }
        if (f.delete()) count[0]++; else ok = false;   // a symlink is removed itself, its target is untouched
        return ok;
    }
}
