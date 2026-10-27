package com.jpm.hrms.document.storage;

import com.jpm.hrms.config.AppProperties;
import com.jpm.hrms.document.CandidateDocument.Storage;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks the storage for new uploads (app.files.storage) and the right storage for an existing document, so
 * switching to another storage later keeps older files readable.
 */
@Component
public class FileStorages {
    private final Map<Storage, FileStorage> byKind = new EnumMap<>(Storage.class);
    private final FileStorage forNewFiles;

    public FileStorages(List<FileStorage> storages, AppProperties props) {
        storages.forEach(s -> byKind.put(s.kind(), s));
        String configured = props.files().storage().trim().toUpperCase(Locale.ROOT);
        Storage kind;
        try {
            kind = Storage.valueOf(configured);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.files.storage must be 'local' or 'gridfs', not '" + props.files().storage() + "'");
        }
        if (kind == Storage.DRIVE || !byKind.containsKey(kind)) {
            throw new IllegalStateException("app.files.storage must be 'local' or 'gridfs'");
        }
        this.forNewFiles = byKind.get(kind);
    }

    public FileStorage forNewFiles() {
        return forNewFiles;
    }

    /** The storage holding an existing document; null for Google Drive documents (read through the Drive API). */
    public FileStorage of(Storage kind) {
        return byKind.get(kind);
    }
}
