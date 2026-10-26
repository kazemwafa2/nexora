package com.nexora.core.pack;

public interface ProgressListener {
    void onProgress(long doneBytes, long totalBytes, String currentFile);
}
