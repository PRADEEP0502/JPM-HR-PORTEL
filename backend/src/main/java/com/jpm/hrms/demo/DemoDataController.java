package com.jpm.hrms.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/demo-data")
@PreAuthorize("hasRole('ADMIN')")
public class DemoDataController {
    private final DemoDataService service;

    public DemoDataController(DemoDataService service) {
        this.service = service;
    }

    /** Adds 5 sample candidates tagged DEMO, spread over the active departments. */
    @PostMapping
    public Map<String, Integer> addSamples() {
        return Map.of("added", service.addSamples(5));
    }

    @DeleteMapping
    public Map<String, Integer> purge() {
        return Map.of("removed", service.purge());
    }

    /** Seeds demo data on startup, only when demo mode is enabled and the database has no candidates. */
    @Component
    @Order(2)
    static class Seeder implements ApplicationRunner {
        private final DemoDataService service;

        Seeder(DemoDataService service) {
            this.service = service;
        }

        @Override
        public void run(ApplicationArguments args) {
            service.seedIfEmpty();
            service.backfillDemoFamilyReferences();
        }
    }
}
