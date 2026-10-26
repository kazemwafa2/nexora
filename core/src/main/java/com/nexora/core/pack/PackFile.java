package com.nexora.core.pack;

public final class PackFile {
    public final String path;
    public final String sha256;
    public final long size;

    public PackFile(String path, String sha256, long size) {
        this.path = path;
        this.sha256 = sha256;
        this.size = size;
    }
}
