package com.nexora.core.pack;

import com.nexora.core.util.Sha256;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class PackVerifier {
    private PackVerifier() { }

    /** Returns problems; empty list means every file matched. Never throws for bad input. */
    public static List<String> verify(File dir, PackManifest m, CancelToken cancel) {
        List<String> problems = new ArrayList<String>();
        for (PackFile pf : m.files) {
            if (cancel != null && cancel.isCancelled()) {
                problems.add("cancelled");
                return problems;
            }
            File f = new File(dir, pf.path);
            if (!f.isFile()) {
                problems.add("missing: " + pf.path);
                continue;
            }
            if (f.length() != pf.size) {
                problems.add("size mismatch: " + pf.path + " (" + f.length() + " != " + pf.size + ")");
                continue;
            }
            try {
                String h = Sha256.ofFile(f);
                if (!h.equals(pf.sha256)) problems.add("hash mismatch: " + pf.path);
            } catch (IOException e) {
                problems.add("unreadable: " + pf.path + " (" + e.getMessage() + ")");
            }
        }
        return problems;
    }
}
