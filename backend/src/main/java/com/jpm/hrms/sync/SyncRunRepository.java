package com.jpm.hrms.sync;

import com.jpm.hrms.common.CriteriaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SyncRunRepository extends CriteriaRepository<SyncRun, Long> {
    Optional<SyncRun> findTopByOrderByStartedAtDesc();

    Optional<SyncRun> findTopByStatusInOrderByStartedAtDesc(List<SyncRun.Status> statuses);

    Page<SyncRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<SyncRun> findTop5ByStatusAndStartedAtAfterOrderByStartedAtDesc(SyncRun.Status status, Instant after);
}
