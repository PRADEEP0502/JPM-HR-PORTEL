package com.jpm.hrms.config;

import com.jpm.hrms.common.CriteriaMongoRepository;
import com.jpm.hrms.common.SequencedEntity;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions.BigDecimalRepresentation;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.time.LocalDate;
import java.util.List;

/**
 * MongoDB mapping rules shared by every collection:
 * <ul>
 *   <li>numeric ids from the {@code counters} collection (see {@link SequencedEntity});</li>
 *   <li>{@code LocalDate} stored as an ISO {@code "yyyy-MM-dd"} string: sortable, range-comparable, no time-zone shift;</li>
 *   <li>{@code BigDecimal} stored as Decimal128 so numeric range filters work.</li>
 * </ul>
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.jpm.hrms", repositoryBaseClass = CriteriaMongoRepository.class)
public class MongoConfig {
    public static final String COUNTERS = "counters";

    @Bean
    MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(c -> c
                .bigDecimal(BigDecimalRepresentation.DECIMAL128)
                .registerConverters(List.of(new LocalDateWriter(), new LocalDateReader())));
    }

    /** Uses the driver-level database (not MongoTemplate), which would otherwise depend on this callback itself. */
    @Bean
    BeforeConvertCallback<Object> sequencedEntityCallback(MongoDatabaseFactory factory) {
        return (entity, collection) -> {
            if (entity instanceof SequencedEntity e) {
                if (e.getId() == null) {
                    e.assignId(nextId(factory, collection));
                    e.onCreate();
                } else {
                    e.onUpdate();
                }
            }
            return entity;
        };
    }

    /** Atomically increments and returns the counter for a collection. */
    public static long nextId(MongoDatabaseFactory factory, String collection) {
        Document counter = factory.getMongoDatabase().getCollection(COUNTERS).findOneAndUpdate(
                Filters.eq("_id", collection), Updates.inc("seq", 1L),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        return ((Number) counter.get("seq")).longValue();
    }

    /** Raises a collection's counter so the next id is above {@code minimum} (used after importing existing rows). */
    public static void ensureCounterAtLeast(MongoOperations ops, String collection, long minimum) {
        ops.upsert(new Query(Criteria.where("_id").is(collection)), new Update().max("seq", minimum), COUNTERS);
    }

    @WritingConverter
    static class LocalDateWriter implements Converter<LocalDate, String> {
        @Override
        public String convert(LocalDate source) {
            return source.toString();
        }
    }

    @ReadingConverter
    static class LocalDateReader implements Converter<String, LocalDate> {
        @Override
        public LocalDate convert(String source) {
            return LocalDate.parse(source);
        }
    }
}
