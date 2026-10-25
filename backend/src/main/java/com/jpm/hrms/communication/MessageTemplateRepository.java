package com.jpm.hrms.communication;

import com.jpm.hrms.communication.MessageTemplate.TemplateChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {
    Optional<MessageTemplate> findFirstByChannelAndActiveTrueOrderByVersionDesc(TemplateChannel channel);

    List<MessageTemplate> findByChannelOrderByVersionDesc(TemplateChannel channel);

    @Query("select coalesce(max(t.version), 0) from MessageTemplate t where t.channel = :channel")
    int maxVersion(@Param("channel") TemplateChannel channel);
}
