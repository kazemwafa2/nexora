package com.nexora.core.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class LaunchRequest {
    private static final Pattern CONTAINER = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
    public static final int MAX_ARGS = 32;
    public static final int MAX_ARG_LEN = 1024;

    public final String containerId;
    public final String exePath;
    public final List<String> args;
    public final int width;
    public final int height;

    public LaunchRequest(String containerId, String exePath, List<String> args, int width, int height) {
        this.containerId = containerId;
        this.exePath = exePath;
        this.args = Collections.unmodifiableList(new ArrayList<String>(args));
        this.width = width;
        this.height = height;
    }

    /** Returns null if valid, otherwise a short reason. */
    public String validate() {
        if (containerId == null || !CONTAINER.matcher(containerId).matches()) return "invalid container id";
        if (exePath == null || exePath.length() == 0 || exePath.length() > 4096) return "invalid executable path";
        if (exePath.indexOf('\0') >= 0) return "NUL in path";
        String low = exePath.toLowerCase();
        if (!(low.endsWith(".exe") || low.endsWith(".msi"))) return "only .exe and .msi can be launched";
        if (args.size() > MAX_ARGS) return "too many arguments";
        for (String a : args) {
            if (a == null || a.length() > MAX_ARG_LEN || a.indexOf('\0') >= 0) return "invalid argument";
        }
        if (width < 320 || width > 8192 || height < 200 || height > 8192) return "resolution out of range";
        return null;
    }
}
