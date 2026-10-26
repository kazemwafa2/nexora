package com.nexora.core.license;

public enum Verdict {
    /** Verified permissive license: may ship. */
    ALLOWED,
    /** Verified weak-copyleft or attribution-sensitive: ship only after the compliance checklist. */
    REVIEW_REQUIRED,
    /** Must not ship in the app. */
    DENIED,
    /** License or origin not verified from the official source: treated as NOT allowed to ship. */
    UNCONFIRMED
}
