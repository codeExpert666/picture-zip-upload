package com.example.picturezipupload.maintenance;

import java.io.IOException;

/**
 * 在安全进度点持久化回填游标。
 */
@FunctionalInterface
public interface PictureBackfillProgressListener {

    void onProgress(PictureBackfillProgress progress) throws IOException;
}
