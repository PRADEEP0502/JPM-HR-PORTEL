package com.jpm.hrms.document.storage;

import com.jpm.hrms.document.CandidateDocument.Storage;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Files in MongoDB GridFS (collections {@code fs.files} / {@code fs.chunks}), separate from candidate documents.
 * No server disk is needed and files are part of every mongodump backup. Mind the Atlas Free tier storage size.
 */
@Component
public class GridFsFileStorage implements FileStorage {
    private final GridFsTemplate gridFs;

    public GridFsFileStorage(GridFsTemplate gridFs) {
        this.gridFs = gridFs;
    }

    @Override
    public Storage kind() {
        return Storage.GRIDFS;
    }

    @Override
    public String store(String folder, String suffix, byte[] content) {
        // The stored name is not the uploaded file name; metadata only links the file to its candidate folder.
        ObjectId id = gridFs.store(new ByteArrayInputStream(content), folder + "." + suffix, new Document("folder", folder));
        return id.toHexString();
    }

    @Override
    public byte[] read(String key) throws IOException {
        GridFSFile file = find(key);
        if (file == null) throw new IOException("Stored file not found");
        try (InputStream in = gridFs.getResource(file).getInputStream()) {
            return in.readAllBytes();
        }
    }

    @Override
    public boolean exists(String key) {
        return find(key) != null;
    }

    @Override
    public void delete(String key) {
        if (ObjectId.isValid(key)) gridFs.delete(new Query(Criteria.where("_id").is(new ObjectId(key))));
    }

    private GridFSFile find(String key) {
        return ObjectId.isValid(key) ? gridFs.findOne(new Query(Criteria.where("_id").is(new ObjectId(key)))) : null;
    }
}
