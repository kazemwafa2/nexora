package com.nexora.core;

import static com.nexora.testing.Assert.*;

import com.nexora.core.util.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonTest {
    public void testEscapesSpecialCharacters() {
        assertEquals("a\\\"b\\\\c\\n\\t\\r", Json.escape("a\"b\\c\n\t\r"));
        assertEquals("\\u0001\\u001b\\u007f", Json.escape("\u0001\u001b\u007f"));
        assertEquals("\\u2028\\u2029", Json.escape("\u2028\u2029"));
    }

    public void testLoneSurrogatesAreReplaced() {
        assertEquals("a\\ufffdb", Json.escape("a\ud800b"));
        assertEquals("a\\ufffdb", Json.escape("a\udc00b"));
        assertEquals("\ud83d\ude00", Json.escape("\ud83d\ude00")); // valid pair is kept
    }

    @SuppressWarnings("unchecked")
    public void testRoundTripOfNastyStrings() throws Exception {
        String[] nasty = {"", "plain", "quote\"inside", "back\\slash", "line\nbreak", "tab\there", "\u0000nul",
            "\u202eRTL override", "فارسی و دری — متن راست‌چین", "emoji \ud83d\ude00", "{\"json\":\"inside\"}", "</script>", "path /data/data/x\\y"};
        for (String s : nasty) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("k\"ey", s);
            Object back = Json.parse(Json.stringify(m));
            assertEquals(s, ((Map<String, Object>) back).get("k\"ey"));
        }
    }

    public void testParsesNumbersAndLiterals() throws Exception {
        Object o = Json.parse("[1, -2, 3.5, 1e3, true, false, null, 9223372036854775807]");
        List<?> l = (List<?>) o;
        assertEquals(Long.valueOf(1), l.get(0));
        assertEquals(Long.valueOf(-2), l.get(1));
        assertEquals(Double.valueOf(3.5), l.get(2));
        assertEquals(Double.valueOf(1000.0), l.get(3));
        assertEquals(Boolean.TRUE, l.get(4));
        assertNull(l.get(6));
        assertEquals(Long.valueOf(Long.MAX_VALUE), l.get(7));
    }

    public void testRejectsMalformedInput() {
        String[] bad = {"", "{", "[1,]", "{\"a\":1,}", "{\"a\":1,\"a\":2}", "01", "1.", "-", "\"\\x\"", "\"tab\there\"",
            "[1] x", "nul", "{'a':1}", "\"\\u12\"", "[1 2]", "{\"a\" 1}"};
        for (String s : bad) {
            final String in = s;
            assertThrows(Json.ParseException.class, () -> Json.parse(in));
        }
    }

    public void testDepthLimit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append('[');
        final String deep = sb.toString();
        assertThrows(Json.ParseException.class, () -> Json.parse(deep));
    }

    public void testStringifyRejectsNaN() {
        assertThrows(IllegalArgumentException.class, () -> Json.stringify(Double.valueOf(Double.NaN)));
    }
}
