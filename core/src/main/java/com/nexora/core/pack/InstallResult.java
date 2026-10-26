package com.nexora.core.pack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InstallResult {
    public enum Code { OK, NO_SPACE, CORRUPT, CANCELLED, ALREADY_INSTALLED, IO_ERROR, ABI_MISMATCH, BAD_MANIFEST }

    public final Code code;
    public final String message;
    public final File installedDir;
    public final List<String> problems;

    InstallResult(Code code, String message, File dir, List<String> problems) {
        this.code = code;
        this.message = message;
        this.installedDir = dir;
        this.problems = Collections.unmodifiableList(new ArrayList<String>(problems));
    }

    public boolean isOk() { return code == Code.OK; }
}
