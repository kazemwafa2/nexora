package com.nexora.core.pack;

import java.io.File;

public interface SpaceChecker {
    /** Free bytes on the volume holding dir, or -1 if unknown. */
    long freeBytes(File dir);

    SpaceChecker REAL = new SpaceChecker() {
        @Override public long freeBytes(File dir) {
            File d = dir;
            while (d != null && !d.exists()) d = d.getParentFile();
            return d == null ? -1 : d.getUsableSpace();
        }
    };
}
