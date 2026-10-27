package com.jpm.hrms.common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;
import org.springframework.data.mongodb.repository.support.SimpleMongoRepository;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

/** Repository base class registered in {@link com.jpm.hrms.config.MongoConfig}. */
public class CriteriaMongoRepository<T, ID> extends SimpleMongoRepository<T, ID> implements CriteriaRepository<T, ID> {
    private final MongoOperations ops;
    private final MongoEntityInformation<T, ID> info;

    public CriteriaMongoRepository(MongoEntityInformation<T, ID> info, MongoOperations ops) {
        super(info, ops);
        this.ops = ops;
        this.info = info;
    }

    @Override
    public List<T> findAll(Criteria criteria, Sort sort) {
        return ops.find(new Query(criteria).with(sort), info.getJavaType(), info.getCollectionName());
    }

    @Override
    public Page<T> findAll(Criteria criteria, Pageable pageable) {
        List<T> content = ops.find(new Query(criteria).with(pageable), info.getJavaType(), info.getCollectionName());
        return PageableExecutionUtils.getPage(content, pageable, () -> count(criteria));
    }

    @Override
    public long count(Criteria criteria) {
        return ops.count(new Query(criteria), info.getJavaType(), info.getCollectionName());
    }
}
