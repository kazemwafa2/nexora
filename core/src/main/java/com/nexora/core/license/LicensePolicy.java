package com.nexora.core.license;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Decides whether a dependency may ship. Anything not verified is UNCONFIRMED. */
public final class LicensePolicy {
    private static final Set<String> ALLOWED = new HashSet<String>(Arrays.asList(
            "mit", "apache-2.0", "bsd-2-clause", "bsd-3-clause", "isc", "zlib", "0bsd", "unlicense"));
    private static final Set<String> REVIEW = new HashSet<String>(Arrays.asList(
            "lgpl-2.1-only", "lgpl-2.1-or-later", "lgpl-3.0-only", "lgpl-3.0-or-later", "mpl-2.0", "epl-2.0", "cc-by-4.0"));

    private LicensePolicy() { }

    public static Verdict evaluate(DependencyEntry d) {
        String o = d.origin == null ? "" : d.origin.toLowerCase(Locale.ROOT);
        if (o.contains("proprietary") || o.contains("microsoft")) return Verdict.DENIED;
        if (!d.verified) return Verdict.UNCONFIRMED;
        String s = d.spdx == null ? "" : d.spdx.trim().toLowerCase(Locale.ROOT);
        if (s.length() == 0 || s.equals("unknown") || s.contains("proprietary")) return Verdict.DENIED;
        if (s.startsWith("gpl") || s.startsWith("agpl") || s.startsWith("sspl")) {
            // strong copyleft is acceptable only for separate programs shipped outside the app
            return "engine-pack".equals(d.scope) ? Verdict.REVIEW_REQUIRED : Verdict.DENIED;
        }
        if (ALLOWED.contains(s)) return Verdict.ALLOWED;
        if (REVIEW.contains(s)) return Verdict.REVIEW_REQUIRED;
        return Verdict.UNCONFIRMED;
    }

    static boolean isOwnCode(DependencyEntry d) {
        return d.origin != null && d.origin.toLowerCase(Locale.ROOT).equals("own code");
    }

    /** Entries that block a release of the given scope (third-party only). */
    public static List<DependencyEntry> blockers(List<DependencyEntry> all, String scope) {
        List<DependencyEntry> out = new ArrayList<DependencyEntry>();
        for (DependencyEntry d : all) {
            if (!scope.equals(d.scope)) continue;
            if (isOwnCode(d)) continue;   // the project's own code is governed by its own (still undecided) license
            Verdict v = evaluate(d);
            if (v == Verdict.DENIED || v == Verdict.UNCONFIRMED) out.add(d);
        }
        return out;
    }
}
