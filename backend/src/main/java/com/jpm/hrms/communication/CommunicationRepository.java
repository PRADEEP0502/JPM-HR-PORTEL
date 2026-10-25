package com.jpm.hrms.communication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommunicationRepository extends JpaRepository<Communication, Long> {
    List<Communication> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

    @Modifying
    @Query("delete from Communication c where c.candidateId in :ids")
    int deleteByCandidateIds(@Param("ids") Collection<Long> ids);
}
