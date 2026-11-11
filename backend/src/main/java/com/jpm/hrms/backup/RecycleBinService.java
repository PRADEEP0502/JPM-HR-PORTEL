package com.jpm.hrms.backup;

import com.jpm.hrms.audit.AuditService;
import com.jpm.hrms.common.ApiException;
import com.jpm.hrms.document.CandidateDocument.Storage;
import com.jpm.hrms.document.storage.FileStorages;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps a full copy of candidates removed by "Delete all candidates" for {@link #KEEP} so they can be restored:
 * the candidate rows with their interviews, remarks, histories, messages and document records. Stored files are
 * only deleted when the copy expires, so restored documents still open.
 */
@Service
public class RecycleBinService {
    private static final Logger log = LoggerFactory.getLogger(RecycleBinService.class);
    static final String BATCHES = "recycle_batches";
    static final String ITEMS = "recycle_bin";
    public static final Duration KEEP = Duration.ofDays(30);
    /** Collections that hang off a candidate through their candidateId field, restored after the candidates. */
    static final List<String> LINKED = List.of("interviews", "hr_remarks", "recruitment_status_history",
            "profile_rating_history", "communications", "candidate_documents");

    public record Batch(String id, Instant deletedAt, String deletedBy, Instant expiresAt, int candidates, List<String> names) {
    }

    public record RestoreResult(int restored, int skipped, List<String> skippedNames) {
    }

    private final MongoTemplate mongo;
    private final FileStorages storages;
    private final AuditService audit;
    private final Clock clock;

    public RecycleBinService(MongoTemplate mongo, FileStorages storages, AuditService audit, Clock clock) {
        this.mongo = mongo;
        this.storages = storages;
        this.audit = audit;
        this.clock = clock;
    }

    /** Copies the candidates and everything linked to them into the recycle bin. Call before deleting them. */
    public String keep(String actor, List<Long> candidateIds, List<String> ignoredRowKeys) {
        String batchId = UUID.randomUUID().toString();
        List<Document> items = new ArrayList<>();
        for (Document d : mongo.find(Query.query(Criteria.where("_id").in(candidateIds)), Document.class, "candidates")) {
            items.add(item(batchId, "candidates", d));
        }
        for (String coll : LINKED) {
            for (Document d : mongo.find(Query.query(Criteria.where("candidateId").in(candidateIds)), Document.class, coll)) {
                items.add(item(batchId, coll, d));
            }
        }
        for (int i = 0; i < items.size(); i += 500) mongo.insert(items.subList(i, Math.min(items.size(), i + 500)), ITEMS);
        mongo.insert(new Document("_id", batchId)
                .append("deletedAt", Date.from(clock.instant()))
                .append("deletedBy", actor)
                .append("ignoredRowKeys", ignoredRowKeys), BATCHES);
        return batchId;
    }

    public List<Batch> list() {
        List<Batch> out = new ArrayList<>();
        for (Document b : mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "deletedAt")), Document.class, BATCHES)) {
            List<Document> cands = mongo.find(Query.query(Criteria.where("batchId").is(b.getString("_id")).and("collection").is("candidates")),
                    Document.class, ITEMS);
            if (cands.isEmpty()) continue;
            Instant at = b.getDate("deletedAt").toInstant();
            List<String> names = cands.stream().map(c -> c.get("doc", Document.class).getString("fullName")).toList();
            out.add(new Batch(b.getString("_id"), at, b.getString("deletedBy"), at.plus(KEEP), cands.size(), names));
        }
        return out;
    }

    /**
     * Puts the batch back. A candidate is skipped when a candidate with the same id, candidate ID or Google Form
     * row is already in the database (e.g. imported again since), so restoring never creates duplicates.
     */
    public RestoreResult restore(String batchId) {
        Document batch = mongo.findById(batchId, Document.class, BATCHES);
        if (batch == null) throw ApiException.notFound("Recycle bin entry");
        Query ofBatch = Query.query(Criteria.where("batchId").is(batchId));
        List<Document> items = mongo.find(ofBatch, Document.class, ITEMS);
        Set<Object> restoredIds = new HashSet<>();
        Set<String> restoredRowKeys = new HashSet<>();
        List<String> skipped = new ArrayList<>();
        for (Document item : items) {
            if (!"candidates".equals(item.getString("collection"))) continue;
            Document c = item.get("doc", Document.class);
            List<Criteria> clash = new ArrayList<>(List.of(Criteria.where("_id").is(c.get("_id"))));
            if (c.get("candidateCode") != null) clash.add(Criteria.where("candidateCode").is(c.get("candidateCode")));
            if (c.get("sourceRowKey") != null) clash.add(Criteria.where("sourceRowKey").is(c.get("sourceRowKey")));
            if (mongo.exists(Query.query(new Criteria().orOperator(clash)), "candidates")) {
                skipped.add(c.getString("fullName"));
                continue;
            }
            mongo.insert(c, "candidates");
            restoredIds.add(c.get("_id"));
            if (c.getString("sourceRowKey") != null) restoredRowKeys.add(c.getString("sourceRowKey"));
        }
        List<Object> done = new ArrayList<>();
        for (Document item : items) {
            String coll = item.getString("collection");
            Document d = item.get("doc", Document.class);
            boolean mine = "candidates".equals(coll) ? restoredIds.contains(d.get("_id")) : restoredIds.contains(d.get("candidateId"));
            if (!mine) continue;
            if (!"candidates".equals(coll) && !mongo.exists(Query.query(Criteria.where("_id").is(d.get("_id"))), coll)) mongo.insert(d, coll);
            done.add(item.get("_id"));
        }
        // the delete marked their form rows as ignored; restored candidates own those rows again
        List<String> keys = batch.getList("ignoredRowKeys", String.class, List.of()).stream().filter(restoredRowKeys::contains).toList();
        if (!keys.isEmpty()) mongo.remove(Query.query(Criteria.where("_id").in(keys)), "sync_ignored_rows");
        if (!done.isEmpty()) mongo.remove(Query.query(Criteria.where("_id").in(done)), ITEMS);
        if (!mongo.exists(ofBatch, ITEMS)) mongo.remove(Query.query(Criteria.where("_id").is(batchId)), BATCHES);
        audit.record("CANDIDATES_RESTORED", "CANDIDATE", null, "Restored " + restoredIds.size() + " candidates from the recycle bin"
                + (skipped.isEmpty() ? "" : "; skipped " + skipped.size() + " already in the database"));
        return new RestoreResult(restoredIds.size(), skipped.size(), skipped);
    }

    /** Removes entries older than {@link #KEEP}, with their stored files. */
    @Scheduled(initialDelay = 300_000, fixedDelay = 3_600_000)
    public void purgeExpired() {
        Date cutoff = Date.from(clock.instant().minus(KEEP));
        for (Document b : mongo.find(Query.query(Criteria.where("deletedAt").lt(cutoff)), Document.class, BATCHES)) {
            String batchId = b.getString("_id");
            for (Document item : mongo.find(Query.query(Criteria.where("batchId").is(batchId).and("collection").is("candidate_documents")),
                    Document.class, ITEMS)) {
                Document d = item.get("doc", Document.class);
                try {
                    if (d.getString("storage") != null && d.getString("storageRef") != null) {
                        storages.of(Storage.valueOf(d.getString("storage"))).delete(d.getString("storageRef"));
                    }
                } catch (Exception e) {
                    log.warn("Could not delete stored file {}: {}", d.getString("storageRef"), e.getMessage());
                }
            }
            mongo.remove(Query.query(Criteria.where("batchId").is(batchId)), ITEMS);
            mongo.remove(Query.query(Criteria.where("_id").is(batchId)), BATCHES);
            log.info("Recycle bin entry {} from {} expired and was removed", batchId, b.getDate("deletedAt"));
        }
    }

    private static Document item(String batchId, String collection, Document doc) {
        return new Document("batchId", batchId).append("collection", collection).append("doc", doc);
    }
}
