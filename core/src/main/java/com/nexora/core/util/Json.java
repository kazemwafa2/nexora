package com.nexora.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small, strict JSON reader/writer with no dependencies (usable in plain JVM tests and on Android).
 * Parsed types: Map (LinkedHashMap), List (ArrayList), String, Long, Double, Boolean, null.
 */
public final class Json {
    private static final int MAX_DEPTH = 64;

    private Json() { }

    // ---------------------------------------------------------------- writer

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    if (Character.isHighSurrogate(c)) {
                        if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                            b.append(c).append(s.charAt(i + 1));
                            i++;
                        } else {
                            b.append("\\ufffd");
                        }
                    } else if (Character.isLowSurrogate(c)) {
                        b.append("\\ufffd");
                    } else if (c < 0x20 || c == 0x7f || c == 0x2028 || c == 0x2029) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.toString();
    }

    public static String quote(String s) {
        return "\"" + escape(s) + "\"";
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("JSON too deep");
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            sb.append(quote((String) v));
        } else if (v instanceof Boolean) {
            sb.append(((Boolean) v).booleanValue() ? "true" : "false");
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new IllegalArgumentException("NaN/Infinity not allowed");
            sb.append(d);
        } else if (v instanceof Number) {
            sb.append(((Number) v).longValue());
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(quote(String.valueOf(e.getKey()))).append(':');
                write(sb, e.getValue(), depth + 1);
            }
            sb.append('}');
        } else if (v instanceof Collection) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Collection<?>) v) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o, depth + 1);
            }
            sb.append(']');
        } else {
            sb.append(quote(String.valueOf(v)));
        }
    }

    // ---------------------------------------------------------------- parser

    public static final class ParseException extends Exception {
        private static final long serialVersionUID = 1L;

        public ParseException(String msg, int pos) { super(msg + " at position " + pos); }
    }

    public static Object parse(String text) throws ParseException {
        if (text == null) throw new ParseException("null input", 0);
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value(0);
        p.skipWs();
        if (p.pos != text.length()) throw new ParseException("trailing characters", p.pos);
        return v;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) { this.s = s; }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++; else break;
            }
        }

        Object value(int depth) throws ParseException {
            if (depth > MAX_DEPTH) throw new ParseException("nesting too deep", pos);
            if (pos >= s.length()) throw new ParseException("unexpected end", pos);
            char c = s.charAt(pos);
            if (c == '{') return object(depth);
            if (c == '[') return array(depth);
            if (c == '"') return string();
            if (c == 't') return literal("true", Boolean.TRUE);
            if (c == 'f') return literal("false", Boolean.FALSE);
            if (c == 'n') return literal("null", null);
            if (c == '-' || (c >= '0' && c <= '9')) return number();
            throw new ParseException("unexpected character '" + c + "'", pos);
        }

        Object literal(String word, Object val) throws ParseException {
            if (!s.startsWith(word, pos)) throw new ParseException("bad literal", pos);
            pos += word.length();
            return val;
        }

        Map<String, Object> object(int depth) throws ParseException {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            pos++;
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') { pos++; return m; }
            while (true) {
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != '"') throw new ParseException("expected string key", pos);
                String k = string();
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != ':') throw new ParseException("expected ':'", pos);
                pos++;
                skipWs();
                if (m.containsKey(k)) throw new ParseException("duplicate key '" + k + "'", pos);
                m.put(k, value(depth + 1));
                skipWs();
                if (pos >= s.length()) throw new ParseException("unterminated object", pos);
                char c = s.charAt(pos++);
                if (c == '}') return m;
                if (c != ',') throw new ParseException("expected ',' or '}'", pos - 1);
            }
        }

        List<Object> array(int depth) throws ParseException {
            List<Object> l = new ArrayList<Object>();
            pos++;
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') { pos++; return l; }
            while (true) {
                skipWs();
                l.add(value(depth + 1));
                skipWs();
                if (pos >= s.length()) throw new ParseException("unterminated array", pos);
                char c = s.charAt(pos++);
                if (c == ']') return l;
                if (c != ',') throw new ParseException("expected ',' or ']'", pos - 1);
            }
        }

        String string() throws ParseException {
            pos++;
            StringBuilder b = new StringBuilder();
            while (true) {
                if (pos >= s.length()) throw new ParseException("unterminated string", pos);
                char c = s.charAt(pos++);
                if (c == '"') return b.toString();
                if (c < 0x20) throw new ParseException("control character in string", pos - 1);
                if (c != '\\') { b.append(c); continue; }
                if (pos >= s.length()) throw new ParseException("bad escape", pos);
                char e = s.charAt(pos++);
                switch (e) {
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'u':
                        if (pos + 4 > s.length()) throw new ParseException("bad \\u escape", pos);
                        int cp = 0;
                        for (int i = 0; i < 4; i++) {
                            int d = Character.digit(s.charAt(pos + i), 16);
                            if (d < 0) throw new ParseException("bad \\u escape", pos);
                            cp = cp * 16 + d;
                        }
                        pos += 4;
                        b.append((char) cp);
                        break;
                    default:
                        throw new ParseException("bad escape '\\" + e + "'", pos - 1);
                }
            }
        }

        Object number() throws ParseException {
            int start = pos;
            if (s.charAt(pos) == '-') pos++;
            if (pos >= s.length()) throw new ParseException("bad number", start);
            if (s.charAt(pos) == '0') {
                pos++;
                if (pos < s.length() && Character.isDigit(s.charAt(pos))) throw new ParseException("leading zero", pos);
            } else if (Character.isDigit(s.charAt(pos))) {
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            } else {
                throw new ParseException("bad number", start);
            }
            boolean isInt = true;
            if (pos < s.length() && s.charAt(pos) == '.') {
                isInt = false;
                pos++;
                int d0 = pos;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                if (pos == d0) throw new ParseException("bad fraction", pos);
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                isInt = false;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                int d0 = pos;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                if (pos == d0) throw new ParseException("bad exponent", pos);
            }
            String t = s.substring(start, pos);
            try {
                if (isInt) return Long.valueOf(Long.parseLong(t));
                return Double.valueOf(Double.parseDouble(t));
            } catch (NumberFormatException ex) {
                if (isInt) return Double.valueOf(Double.parseDouble(t));
                throw new ParseException("bad number", start);
            }
        }
    }
}
