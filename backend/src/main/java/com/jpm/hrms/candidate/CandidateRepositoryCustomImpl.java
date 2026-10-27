package com.jpm.hrms.candidate;

import com.jpm.hrms.candidate.CandidateEnums.ApplicationStatus;
import com.jpm.hrms.candidate.CandidateEnums.InterviewStatus;
import com.jpm.hrms.candidate.CandidateEnums.VerificationStatus;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.skip;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

class CandidateRepositoryCustomImpl implements CandidateRepositoryCustom {
    private static final String COLLECTION = "candidates";
    private final MongoOperations ops;

    CandidateRepositoryCustomImpl(MongoOperations ops) {
        this.ops = ops;
    }

    @Override
    public List<Object[]> countGroupedByApplicationStatus() {
        List<Object[]> out = new ArrayList<>();
        for (Document d : groupCount("applicationStatus")) {
            out.add(new Object[]{ApplicationStatus.valueOf(d.getString("_id")), count(d)});
        }
        return out;
    }

    @Override
    public List<Object[]> countGroupedByInterviewStatus() {
        List<Object[]> out = new ArrayList<>();
        for (Document d : groupCount("interviewStatus")) {
            out.add(new Object[]{InterviewStatus.valueOf(d.getString("_id")), count(d)});
        }
        return out;
    }

    @Override
    public List<BigDecimal> findAllExperienceYears() {
        Query q = new Query();
        q.fields().include("experienceYears").exclude("_id");
        List<BigDecimal> out = new ArrayList<>();
        for (Document d : ops.find(q, Document.class, COLLECTION)) out.add(decimal(d.get("experienceYears")));
        return out;
    }

    @Override
    public List<LocalDate> findApplicationDatesSince(LocalDate from) {
        Query q = new Query(Criteria.where("applicationDate").gte(from.toString()));
        q.fields().include("applicationDate").exclude("_id");
        List<LocalDate> out = new ArrayList<>();
        for (Document d : ops.find(q, Document.class, COLLECTION)) out.add(LocalDate.parse(d.getString("applicationDate")));
        return out;
    }

    @Override
    public List<Object[]> countGroupedByDepartmentAndStatus() {
        var agg = newAggregation(group("departmentId", "applicationStatus", "verificationStatus").count().as("n"));
        List<Object[]> out = new ArrayList<>();
        for (Document d : ops.aggregate(agg, COLLECTION, Document.class).getMappedResults()) {
            Document id = d.get("_id", Document.class);
            out.add(new Object[]{longOrNull(id.get("departmentId")), ApplicationStatus.valueOf(id.getString("applicationStatus")),
                    VerificationStatus.valueOf(id.getString("verificationStatus")), count(d)});
        }
        return out;
    }

    @Override
    public List<Object[]> countOnDateByDepartment(LocalDate today) {
        var agg = newAggregation(match(Criteria.where("applicationDate").is(today.toString())),
                group("departmentId").count().as("n"));
        List<Object[]> out = new ArrayList<>();
        for (Document d : ops.aggregate(agg, COLLECTION, Document.class).getMappedResults()) {
            out.add(new Object[]{longOrNull(d.get("_id")), count(d)});
        }
        return out;
    }

    @Override
    public Page<Candidate> findAllOrdered(Criteria criteria, AggregationOperation sortKey, Sort sort, Pageable pageable) {
        List<AggregationOperation> stages = new ArrayList<>(List.of(match(criteria), sortKey, sort(sort)));
        stages.add(skip(pageable.getOffset()));
        stages.add(limit(pageable.getPageSize()));
        List<Candidate> content = ops.aggregate(newAggregation(Candidate.class, stages), COLLECTION, Candidate.class)
                .getMappedResults();
        return PageableExecutionUtils.getPage(content, pageable, () -> ops.count(new Query(criteria), Candidate.class, COLLECTION));
    }

    @Override
    public List<Candidate> findAllOrdered(Criteria criteria, AggregationOperation sortKey, Sort sort) {
        return ops.aggregate(newAggregation(Candidate.class, match(criteria), sortKey, sort(sort)), COLLECTION, Candidate.class)
                .getMappedResults();
    }

    private List<Document> groupCount(String field) {
        return ops.aggregate(newAggregation(group(field).count().as("n")), COLLECTION, Document.class).getMappedResults();
    }

    private static long count(Document d) {
        return ((Number) d.get("n")).longValue();
    }

    private static Long longOrNull(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) return null;
        if (v instanceof Decimal128 d) return d.bigDecimalValue();
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }
}
