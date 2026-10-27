package com.jpm.hrms.common;

/**
 * A MongoDB document with a numeric, human-friendly id (1, 2, 3 ...) taken from the {@code counters} collection,
 * so URLs and API payloads never expose MongoDB ObjectIds. {@link com.jpm.hrms.config.MongoConfig} assigns the id
 * and calls the lifecycle hooks before every save.
 */
public interface SequencedEntity {
    Long getId();

    void assignId(Long id);

    /** Called once, before the first insert. */
    default void onCreate() {
    }

    /** Called before every later save. */
    default void onUpdate() {
    }
}
