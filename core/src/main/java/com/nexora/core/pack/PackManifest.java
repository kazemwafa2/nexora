package com.nexora.core.pack;

import com.nexora.core.util.Json;
import com.nexora.core.util.Sha256;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strictly validated description of an engine pack. A manifest that fails validation is rejected whole. */
public final class PackManifest {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z._+-]{0,31}");
    public static final int MAX_FILES = 20000;

    public final String packId;
    public final String version;
    public final String abi;
    public final List<PackFile> files;
    public final List<String> licenseFiles;
    public final long totalSize;

    private PackManifest(String id, String ver, String abi, List<PackFile> files, List<String> lic, long total) {
        this.packId = id;
        this.version = ver;
        this.abi = abi;
        this.files = Collections.unmodifiableList(files);
        this.licenseFiles = Collections.unmodifiableList(lic);
        this.totalSize = total;
    }

    public static final class Invalid extends Exception {
        private static final long serialVersionUID = 1L;

        public Invalid(String m) { super(m); }
    }

    @SuppressWarnings("unchecked")
    public static PackManifest parse(String json) throws Invalid {
        Object root;
        try {
            root = Json.parse(json);
        } catch (Json.ParseException e) {
            throw new Invalid("not valid JSON: " + e.getMessage());
        }
        if (!(root instanceof Map)) throw new Invalid("manifest must be an object");
        Map<String, Object> m = (Map<String, Object>) root;
        String id = str(m, "pack_id");
        String ver = str(m, "version");
        String abi = str(m, "abi");
        if (!ID.matcher(id).matches()) throw new Invalid("bad pack_id");
        if (!VERSION.matcher(ver).matches()) throw new Invalid("bad version");
        if (abi.length() == 0 || abi.length() > 32) throw new Invalid("bad abi");
        Object fo = m.get("files");
        if (!(fo instanceof List)) throw new Invalid("files must be an array");
        List<Object> raw = (List<Object>) fo;
        if (raw.isEmpty()) throw new Invalid("files is empty");
        if (raw.size() > MAX_FILES) throw new Invalid("too many files");
        List<PackFile> files = new ArrayList<PackFile>();
        Set<String> seen = new HashSet<String>();
        long total = 0;
        for (Object o : raw) {
            if (!(o instanceof Map)) throw new Invalid("file entry must be an object");
            Map<String, Object> fm = (Map<String, Object>) o;
            String path = str(fm, "path");
            String why = badPath(path);
            if (why != null) throw new Invalid("unsafe path '" + path + "': " + why);
            if (!seen.add(path)) throw new Invalid("duplicate path '" + path + "'");
            String sha = str(fm, "sha256");
            if (!Sha256.isHex64(sha)) throw new Invalid("bad sha256 for " + path);
            Object so = fm.get("size");
            if (!(so instanceof Long) || ((Long) so).longValue() < 0) throw new Invalid("bad size for " + path);
            long size = ((Long) so).longValue();
            total += size;
            if (total < 0) throw new Invalid("size overflow");
            files.add(new PackFile(path, sha, size));
        }
        List<String> lic = new ArrayList<String>();
        Object lo = m.get("license_files");
        if (lo != null) {
            if (!(lo instanceof List)) throw new Invalid("license_files must be an array");
            for (Object o : (List<Object>) lo) {
                if (!(o instanceof String)) throw new Invalid("license file must be a string");
                if (!seen.contains((String) o)) throw new Invalid("license file not listed in files: " + o);
                lic.add((String) o);
            }
        }
        return new PackManifest(id, ver, abi, files, lic, total);
    }

    private static String str(Map<String, Object> m, String k) throws Invalid {
        Object v = m.get(k);
        if (!(v instanceof String) || ((String) v).length() == 0) throw new Invalid("missing or empty '" + k + "'");
        return (String) v;
    }

    /** Returns null if the relative path is safe, else the reason. */
    public static String badPath(String p) {
        if (p.length() > 512) return "too long";
        if (p.startsWith("/")) return "absolute";
        if (p.indexOf('\\') >= 0) return "backslash";
        if (p.indexOf('\0') >= 0) return "NUL";
        if (p.endsWith("/")) return "directory";
        for (String seg : p.split("/", -1)) {
            if (seg.length() == 0) return "empty segment";
            if (seg.equals(".") || seg.equals("..")) return "dot segment";
        }
        return null;
    }
}
