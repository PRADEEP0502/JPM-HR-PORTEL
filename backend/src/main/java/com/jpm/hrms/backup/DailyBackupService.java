package com.jpm.hrms.backup;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.common.ApiException;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * A copy of every database record (candidates, interviews, users, settings, audit log, …) taken once a day and kept
 * for {@link #KEEP_COUNT} days. Backups live in the database's own "backups" GridFS bucket because the server's disk is
 * wiped on restart; an admin can download one from Settings to keep a copy elsewhere. Uploaded document files
 * themselves are not included (they would fill the database quickly); their records are.
 *
 * <p>File format: gzip of one JSON line per record, {"c": "&lt;collection&gt;", "d": {...}} in MongoDB Extended JSON.
 */
@Service
public class DailyBackupService {
    private static final Logger log = LoggerFactory.getLogger(DailyBackupService.class);
    static final String BUCKET = "backups";
    public static final int KEEP_COUNT = 14;
    private static final Duration EVERY = Duration.ofHours(24);
    private static final JsonWriterSettings JSON = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();

    public record BackupInfo(String id, String fileName, long size, Instant createdAt, String createdBy) {
    }

    public record BackupFile(String fileName, byte[] content) {
    }

    private final MongoTemplate mongo;
    private final AuditService audit;
    private final Clock clock;
    private final ZoneId zone;

    public DailyBackupService(MongoTemplate mongo, AuditService audit, Clock clock, ZoneId businessZone) {
        this.mongo = mongo;
        this.audit = audit;
        this.clock = clock;
        this.zone = businessZone;
    }

    /** Checked every hour rather than at a fixed time, so a server that was asleep catches up when it wakes. */
    @Scheduled(initialDelay = 180_000, fixedDelay = 3_600_000)
    public void backupIfDue() {
        List<BackupInfo> all = list();
        if (!all.isEmpty() && all.get(0).createdAt().isAfter(clock.instant().minus(EVERY))) return;
        try {
            BackupInfo b = backup(AuditService.SYSTEM_ACTOR);
            log.info("Daily backup {} written ({} bytes)", b.fileName(), b.size());
        } catch (RuntimeException e) {
            log.error("Daily backup failed: {}", e.getMessage(), e);
        }
    }

    public BackupInfo backup(String actor) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int records = 0;
        try (Writer out = new OutputStreamWriter(new GZIPOutputStream(bytes), StandardCharsets.UTF_8)) {
            for (String coll : mongo.getDb().listCollectionNames()) {
                if (coll.startsWith("fs.") || coll.startsWith(BUCKET + ".") || coll.startsWith("system.")) continue;
                for (Document d : mongo.getDb().getCollection(coll).find()) {
                    out.write(new Document("c", coll).append("d", d).toJson(JSON));
                    out.write('\n');
                    records++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Instant now = clock.instant();
        String name = "jpm-hrms-backup-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(zone).format(now) + ".jsonl.gz";
        ObjectId id = bucket().uploadFromStream(name, new ByteArrayInputStream(bytes.toByteArray()),
                new GridFSUploadOptions().metadata(new Document("createdBy", actor).append("records", records)));
        prune();
        if (!AuditService.SYSTEM_ACTOR.equals(actor)) {
            audit.record("BACKUP_CREATED", "BACKUP", null, "Backup " + name + " (" + records + " records)");
        }
        return new BackupInfo(id.toHexString(), name, bytes.size(), now, actor);
    }

    /** Newest first. */
    public List<BackupInfo> list() {
        List<BackupInfo> out = new ArrayList<>();
        for (GridFSFile f : bucket().find().sort(new Document("uploadDate", -1))) {
            String by = f.getMetadata() == null ? null : f.getMetadata().getString("createdBy");
            out.add(new BackupInfo(f.getObjectId().toHexString(), f.getFilename(), f.getLength(), f.getUploadDate().toInstant(), by));
        }
        return out;
    }

    public BackupFile download(String id) {
        if (!ObjectId.isValid(id)) throw ApiException.notFound("Backup");
        GridFSFile f = bucket().find(new Document("_id", new ObjectId(id))).first();
        if (f == null) throw ApiException.notFound("Backup");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bucket().downloadToStream(f.getObjectId(), out);
        audit.record("BACKUP_DOWNLOADED", "BACKUP", null, "Downloaded " + f.getFilename());
        return new BackupFile(f.getFilename(), out.toByteArray());
    }

    private void prune() {
        List<BackupInfo> all = list();
        for (BackupInfo old : all.subList(Math.min(KEEP_COUNT, all.size()), all.size())) bucket().delete(new ObjectId(old.id()));
    }

    private GridFSBucket bucket() {
        return GridFSBuckets.create(mongo.getDb(), BUCKET);
    }
}
