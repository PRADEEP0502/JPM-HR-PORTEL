package com.jpm.hrms.communication;

import com.jpm.hrms.common.CriteriaRepository;
import com.jpm.hrms.communication.MessageTemplate.TemplateChannel;

import java.util.List;
import java.util.Optional;

public interface MessageTemplateRepository extends CriteriaRepository<MessageTemplate, Long> {
    Optional<MessageTemplate> findFirstByChannelAndActiveTrueOrderByVersionDesc(TemplateChannel channel);

    List<MessageTemplate> findByChannelOrderByVersionDesc(TemplateChannel channel);

    Optional<MessageTemplate> findFirstByChannelOrderByVersionDesc(TemplateChannel channel);

    /** Highest version number for a channel (0 when there is none yet). */
    default int maxVersion(TemplateChannel channel) {
        return findFirstByChannelOrderByVersionDesc(channel).map(MessageTemplate::getVersion).orElse(0);
    }
}
