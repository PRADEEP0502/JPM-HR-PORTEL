package com.jpm.hrms.communication;

import com.jpm.hrms.common.CriteriaRepository;
import com.jpm.hrms.communication.MessageTemplate.TemplateChannel;

import java.util.List;
import java.util.Optional;

public interface MessageTemplateRepository extends CriteriaRepository<MessageTemplate, Long> {
    Optional<MessageTemplate> findFirstByPurposeAndChannelAndActiveTrueOrderByVersionDesc(TemplatePurpose purpose, TemplateChannel channel);

    List<MessageTemplate> findByPurposeAndChannelOrderByVersionDesc(TemplatePurpose purpose, TemplateChannel channel);

    Optional<MessageTemplate> findFirstByPurposeAndChannelOrderByVersionDesc(TemplatePurpose purpose, TemplateChannel channel);

    /** Highest version number for a purpose and channel (0 when there is none yet). */
    default int maxVersion(TemplatePurpose purpose, TemplateChannel channel) {
        return findFirstByPurposeAndChannelOrderByVersionDesc(purpose, channel).map(MessageTemplate::getVersion).orElse(0);
    }
}
