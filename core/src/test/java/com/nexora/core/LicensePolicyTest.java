package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.license.*;

import java.io.File;
import java.util.List;

public class LicensePolicyTest {
    private DependencyEntry d(String spdx, String origin, boolean verified, String scope) {
        return new DependencyEntry("x", "1", spdx, origin, verified, scope);
    }

    public void testUnverifiedIsNeverAllowed() {
        assertEquals(Verdict.UNCONFIRMED, LicensePolicy.evaluate(d("MIT", "github", false, "app")));
    }

    public void testPermissiveVerifiedIsAllowed() {
        assertEquals(Verdict.ALLOWED, LicensePolicy.evaluate(d("Apache-2.0", "github", true, "app")));
        assertEquals(Verdict.ALLOWED, LicensePolicy.evaluate(d("zlib", "github", true, "engine-pack")));
    }

    public void testWeakCopyleftNeedsReview() {
        assertEquals(Verdict.REVIEW_REQUIRED, LicensePolicy.evaluate(d("LGPL-2.1-or-later", "winehq", true, "engine-pack")));
    }

    public void testGplDeniedInAppButReviewableAsSeparatePack() {
        assertEquals(Verdict.DENIED, LicensePolicy.evaluate(d("GPL-3.0-only", "x", true, "app")));
        assertEquals(Verdict.REVIEW_REQUIRED, LicensePolicy.evaluate(d("GPL-3.0-only", "x", true, "engine-pack")));
    }

    public void testMicrosoftAndProprietaryDeniedEvenIfVerified() {
        assertEquals(Verdict.DENIED, LicensePolicy.evaluate(d("MIT", "Microsoft proprietary DLL", true, "engine-pack")));
        assertEquals(Verdict.DENIED, LicensePolicy.evaluate(d("proprietary", "vendor", true, "app")));
        assertEquals(Verdict.DENIED, LicensePolicy.evaluate(d("", "vendor", true, "app")));
    }

    public void testRepositoryDependencyFileParsesAndHasNoVerifiedLies() throws Exception {
        List<DependencyEntry> all = DependencyCsv.read(new File("config/dependencies.csv"));
        assertTrue(all.size() >= 10);
        for (DependencyEntry e : all) {
            Verdict v = LicensePolicy.evaluate(e);
            assertFalse("nothing may be ALLOWED before its license is verified: " + e.name, v == Verdict.ALLOWED && !e.verified);
        }
        boolean msDenied = false;
        for (DependencyEntry e : all) {
            if (e.origin.toLowerCase().contains("microsoft")) msDenied = LicensePolicy.evaluate(e) == Verdict.DENIED;
        }
        assertTrue("Microsoft binaries must be denied", msDenied);
    }

    public void testBlockersOnlyForRequestedScope() {
        List<DependencyEntry> l = java.util.Arrays.asList(d("GPL-3.0-only", "x", true, "app"), d("MIT", "x", true, "app"),
                d("proprietary", "x", true, "engine-pack"));
        assertEquals(1, LicensePolicy.blockers(l, "app").size());
        assertEquals(1, LicensePolicy.blockers(l, "engine-pack").size());
    }

    public void testOwnCodeDoesNotBlockButThirdPartyUnverifiedStillDoes() {
        List<DependencyEntry> l = java.util.Arrays.asList(
                new DependencyEntry("Nexora core", "0.1", "UNDECIDED", "own code", false, "app"),
                new DependencyEntry("lib", "1", "MIT", "github", false, "app"));
        List<DependencyEntry> b = LicensePolicy.blockers(l, "app");
        assertEquals(1, b.size());
        assertEquals("lib", b.get(0).name);
    }

    public void testShippedInventoryHasNoAppScopeBlockers() throws Exception {
        List<DependencyEntry> all = DependencyCsv.read(new File("config/dependencies.csv"));
        assertEquals(0, LicensePolicy.blockers(all, "app").size());
    }
}
