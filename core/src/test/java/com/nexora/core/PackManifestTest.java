package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.pack.PackManifest;

public class PackManifestTest {
    static final String H = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    static String manifest(String files, String extra) {
        return "{\"pack_id\":\"engine-core\",\"version\":\"1.0\",\"abi\":\"arm64-v8a\",\"files\":" + files + extra + "}";
    }

    static String file(String path) { return "{\"path\":\"" + path + "\",\"sha256\":\"" + H + "\",\"size\":3}"; }

    public void testValidManifest() throws Exception {
        PackManifest m = PackManifest.parse(manifest("[" + file("lib/a.so") + "," + file("LICENSE.txt") + "]", ",\"license_files\":[\"LICENSE.txt\"]"));
        assertEquals(2, m.files.size());
        assertEquals(6, m.totalSize);
        assertEquals("engine-core", m.packId);
    }

    public void testUnsafePathsRejected() {
        String[] bad = {"../x", "a/../b", "/abs", "a//b", "a\\\\b", "./a", "dir/", "a/./b"};
        for (String p : bad) {
            final String json = manifest("[" + file(p) + "]", "");
            assertThrows(PackManifest.Invalid.class, () -> PackManifest.parse(json));
        }
    }

    public void testStructuralErrorsRejected() {
        String[] bad = {
            "[]", "{}", "{\"pack_id\":\"x\"}",
            manifest("[]", ""),
            manifest("[" + file("a") + "," + file("a") + "]", ""),
            manifest("[{\"path\":\"a\",\"sha256\":\"zz\",\"size\":1}]", ""),
            manifest("[{\"path\":\"a\",\"sha256\":\"" + H + "\",\"size\":-1}]", ""),
            manifest("[{\"path\":\"a\",\"sha256\":\"" + H + "\",\"size\":\"3\"}]", ""),
            manifest("[" + file("a") + "]", ",\"license_files\":[\"missing.txt\"]"),
            "{\"pack_id\":\"BAD ID\",\"version\":\"1\",\"abi\":\"arm64-v8a\",\"files\":[" + file("a") + "]}",
            "not json"
        };
        for (String s : bad) {
            final String json = s;
            assertThrows(PackManifest.Invalid.class, () -> PackManifest.parse(json));
        }
    }
}
