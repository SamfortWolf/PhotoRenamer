package com.samfort.photorenamer;

public interface ProgressListener {

    void onStart(int totalFiles);
    void onProgress(RenameResult result);
    void onComplete(int renamed, int skipped, int errors);

}
