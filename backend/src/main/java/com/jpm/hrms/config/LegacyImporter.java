package com.jpm.hrms.config;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ONE-TIME MIGRATION from the previous SQL database (H2 dev file or PostgreSQL) into MongoDB.
 * <p>
 * Runs at startup only when {@code LEGACY_JDBC_URL} is set, and only when MongoDB has no users and no candidates,
 * so it can never overwrite live data. Every table is copied with the same ids (candidate URLs and codes stay the
 * same); column names become camelCase field names. Uploaded files are not touched: they stay in UPLOAD_DIR.
 */
@Component
@Order(-5)
public class LegacyImporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacyImporter.class);

    /** SQL table → MongoDB collection (same names), in dependency order; the id column becomes _id. */
    private static final List<String[]> TABLES = List.of(
            new String[]{"departments", "id"},
            new String[]{"app_users", "id"},
            new String[]{"candidates", "id"},
            new String[]{"hr_remarks", "id"},
            new String[]{"candidate_documents", "id"},
            new String[]{"interviews", "id"},
            new String[]{"message_templates", "id"},
            new String[]{"communication_settings", "id"},
            new String[]{"communications", "id"},
            new String[]{"integration_settings", "id"},
            new String[]{"sync_runs", "id"},
            new String[]{"sync_ignored_rows", "row_key"},
            new String[]{"audit_logs", "id"});

    private final MongoOperations ops;
    private final MongoIndexes indexes;
    private final String url;
    private final String username;
    private final String password;

    public LegacyImporter(MongoOperations ops, MongoIndexes indexes,
                          @Value("${app.legacy-import.jdbc-url:}") String url,
                          @Value("${app.legacy-import.username:}") String username,
                          @Value("${app.legacy-import.password:}") String password) {
        this.ops = ops;
        this.indexes = indexes;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        if (!StringUtils.hasText(url)) return;
        if (ops.getCollection("app_users").countDocuments() > 0 || ops.getCollection("candidates").countDocuments() > 0) {
            log.warn("Legacy import skipped: MongoDB already contains users or candidates. Unset LEGACY_JDBC_URL.");
            return;
        }
        indexes.ensureAll();
        log.info("Legacy import: copying data from {} into MongoDB", url.replaceAll("password=[^;&]*", "password=***"));
        try (Connection con = DriverManager.getConnection(url, username, password)) {
            for (String[] t : TABLES) copy(con, t[0], t[1]);
        }
        log.info("Legacy import finished. Unset LEGACY_JDBC_URL; the old database is no longer used and can be archived.");
    }

    private void copy(Connection con, String table, String idColumn) throws SQLException {
        List<Document> docs = new ArrayList<>();
        long maxId = 0;
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                Document d = new Document();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    String column = md.getColumnLabel(i).toLowerCase();
                    Object value = read(rs, i, md.getColumnType(i));
                    if (value == null) continue;
                    String field = column.equals(idColumn) ? "_id" : camel(column);
                    d.put(field, value);
                    if (field.equals("_id") && value instanceof Number n) maxId = Math.max(maxId, n.longValue());
                }
                docs.add(d);
            }
        } catch (SQLException e) {
            log.warn("Legacy import: table {} could not be read ({}); skipped", table, e.getMessage());
            return;
        }
        if (!docs.isEmpty()) ops.getCollection(table).insertMany(docs);
        if (maxId > 0) MongoConfig.ensureCounterAtLeast(ops, table, maxId);
        log.info("Legacy import: {} -> {} documents", table, docs.size());
    }

    /** Converts JDBC values to the representation the application's MongoDB mapping uses. */
    private static Object read(ResultSet rs, int i, int sqlType) throws SQLException {
        if (sqlType == Types.CLOB || sqlType == Types.NCLOB || sqlType == Types.LONGVARCHAR) return rs.getString(i);
        Object v = rs.getObject(i);
        if (v == null) return null;
        if (v instanceof Integer n && (sqlType == Types.BIGINT)) return n.longValue();
        if (v instanceof BigDecimal b) return new Decimal128(b);
        if (v instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (v instanceof LocalDate d) return d.toString();
        if (v instanceof Timestamp ts) return Date.from(ts.toInstant());
        if (v instanceof OffsetDateTime odt) return Date.from(odt.toInstant());
        if (v instanceof LocalDateTime ldt) return Date.from(ldt.toInstant(ZoneOffset.UTC));
        if (v instanceof Instant in) return Date.from(in);
        return v;
    }

    static String camel(String column) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char ch : column.toCharArray()) {
            if (ch == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        return sb.toString();
    }
}
