package com.nexora.core.license;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** CSV: name,version,spdx,origin,verified(yes/no),scope. Lines starting with # are comments. */
public final class DependencyCsv {
    private DependencyCsv() { }

    public static List<DependencyEntry> read(File f) throws IOException {
        List<DependencyEntry> out = new ArrayList<DependencyEntry>();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        try {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null) {
                n++;
                String t = line.trim();
                if (t.length() == 0 || t.startsWith("#")) continue;
                String[] p = t.split(",", -1);
                if (p.length != 6) throw new IOException("line " + n + ": expected 6 fields, got " + p.length);
                out.add(new DependencyEntry(p[0].trim(), p[1].trim(), p[2].trim(), p[3].trim(),
                        "yes".equalsIgnoreCase(p[4].trim()), p[5].trim()));
            }
        } finally {
            r.close();
        }
        return out;
    }

    /** CI entry point: prints a table; exit code 1 if an "app"-scope dependency blocks a release. */
    public static void main(String[] args) throws IOException {
        List<DependencyEntry> all = read(new File(args.length > 0 ? args[0] : "config/dependencies.csv"));
        for (DependencyEntry d : all) {
            System.out.println(String.format("%-14s %-22s %-8s %-28s %s", LicensePolicy.evaluate(d), d.name + " " + d.version,
                    d.scope, d.spdx, d.verified ? "verified" : "NOT verified"));
        }
        List<DependencyEntry> b = LicensePolicy.blockers(all, "app");
        System.out.println("app-scope blockers: " + b.size());
        System.exit(b.isEmpty() ? 0 : 1);
    }
}
