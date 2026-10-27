package com.jpm.hrms.config;

import com.mongodb.ConnectionString;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.mongo.types.DatabaseDir;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DEVELOPMENT AND TESTS ONLY: starts a real MongoDB server (official binaries, downloaded once) inside the
 * application, so the project runs without installing MongoDB or creating an Atlas account.
 * Enabled by {@code app.embedded-mongo.enabled=true} (dev and test profiles). Production uses MONGODB_URI.
 */
@Configuration
@ConditionalOnProperty(name = "app.embedded-mongo.enabled", havingValue = "true")
public class EmbeddedMongoConfig {
    private static final Logger log = LoggerFactory.getLogger(EmbeddedMongoConfig.class);
    private static final String DATABASE = "jpm_hrms";
    /** One server per JVM, shared by every Spring context (tests create several). */
    private static TransitionWalker.ReachedState<RunningMongodProcess> server;

    @Bean
    MongoConnectionDetails embeddedMongoConnectionDetails(@Value("${app.embedded-mongo.data-dir:}") String dataDir,
                                                         @Value("${app.embedded-mongo.port:0}") int port) {
        RunningMongodProcess mongod = start(dataDir, port);
        String uri = "mongodb://" + mongod.getServerAddress().getHost() + ":" + mongod.getServerAddress().getPort() + "/" + DATABASE;
        ConnectionString cs = new ConnectionString(uri);
        return () -> cs;
    }

    private static synchronized RunningMongodProcess start(String dataDir, int port) {
        if (server != null) return server.current();
        try {
            int p = port > 0 ? port : freePort();
            var mongod = Mongod.instance().withNet(Start.to(Net.class).initializedWith(Net.of("127.0.0.1", p, false)));
            if (StringUtils.hasText(dataDir)) {
                Path dir = Path.of(dataDir).toAbsolutePath().normalize();
                Files.createDirectories(dir);
                mongod = mongod.withDatabaseDir(Start.to(DatabaseDir.class).initializedWith(DatabaseDir.of(dir)));
                log.info("Starting embedded MongoDB (development only) on 127.0.0.1:{} with data in {}", p, dir);
            } else {
                log.info("Starting embedded MongoDB (temporary data, tests) on 127.0.0.1:{}", p);
            }
            server = mongod.start(Version.Main.V7_0);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.close(), "embedded-mongo-shutdown"));
            return server.current();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start embedded MongoDB", e);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
