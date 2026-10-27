package com.jpm.hrms.common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/** Base repository: Spring Data MongoDB plus dynamic filtering with {@link Criteria} (used for search and filter screens). */
@NoRepositoryBean
public interface CriteriaRepository<T, ID> extends MongoRepository<T, ID> {

    List<T> findAll(Criteria criteria, Sort sort);

    Page<T> findAll(Criteria criteria, Pageable pageable);

    long count(Criteria criteria);
}
