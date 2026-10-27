package com.jpm.hrms.document.storage;

import com.jpm.hrms.document.CandidateDocument.Storage;

import java.io.IOException;

/**
 * Private storage for candidate documents. Business code only sees opaque keys; files are never reachable by URL.
 * Implementations: {@link LocalFileStorage} (server folder) and {@link GridFsFileStorage} (MongoDB GridFS).
 * A cloud object store (e.g. a private S3-compatible bucket) is added by implementing this interface.
 */
public interface FileStorage {

    /** Which {@link Storage} value documents stored here carry. */
    Storage kind();

    /**
     * Stores the bytes under a new random key and returns it. {@code folder} groups files (the candidate id) and
     * {@code suffix} is a fixed extension chosen by the server ("pdf", "enc"), never the uploaded file name.
     */
    String store(String folder, String suffix, byte[] content) throws IOException;

    byte[] read(String key) throws IOException;

    boolean exists(String key);

    void delete(String key) throws IOException;
}
