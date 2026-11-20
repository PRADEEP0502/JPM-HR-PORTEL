package com.jpm.hrms.communication;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends the automatic WhatsApp greeting in the background, so a Google Form submission is saved and answered
 * straight away even when the WhatsApp API is slow. One message at a time, in the order applications arrive.
 */
@Component
public class ApplicantGreeter {
    private final CommunicationService service;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "whatsapp-greeting");
        t.setDaemon(true);
        return t;
    });

    public ApplicantGreeter(CommunicationService service) {
        this.service = service;
    }

    /** Queues the greeting for a candidate created from a Google Form response. */
    public void greetLater(Long candidateId, String actor) {
        executor.execute(() -> service.greetNewApplicant(candidateId, actor));
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}
