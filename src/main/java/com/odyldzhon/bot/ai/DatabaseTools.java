package com.odyldzhon.bot.ai;

import com.odyldzhon.bot.persistence.entity.ChatMessageEntity;
import com.odyldzhon.bot.persistence.MessageStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tools the LLM can call to look things up in the chat-message database.
 *
 * Two tools are exposed:
 *   • {@link #searchSimilarMessages(String, Integer)} – preferred semantic search
 *   • {@link #executeSelectQuery(String)}            – generic, read-only SQL escape hatch
 *
 * The schema visible to the model:
 *   chat_message(id BIGINT, created_at TIMESTAMP, author TEXT, message TEXT, embedding vector(1536))
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseTools {

    /** Hard cap on rows returned to the model – prevents context overflow. */
    private static final int  MAX_ROWS         = 100;
    /** Hard cap on response characters returned to the model. */
    private static final int  MAX_CHARS        = 10_000;
    /** Postgres statement timeout (ms) for tool-issued queries. */
    private static final int  STATEMENT_TIMEOUT_MS = 3_000;

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** Tokens that are forbidden anywhere in the SQL (case-insensitive, word-bounded). */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|grant|revoke|"
          + "vacuum|analyze|copy|call|do|merge|comment|begin|commit|rollback|"
          + "set|reset|listen|notify|cluster|reindex|lock|prepare|execute|"
          + "discard|security|pg_)\\b",
            Pattern.CASE_INSENSITIVE);

    private final MessageStore messageStore;
    private final JdbcTemplate jdbcTemplate;

    // -------------------------------------------------------------------------
    // Preferred tool – semantic search
    // -------------------------------------------------------------------------

    @Tool(description = """
            Semantic search over previously stored Telegram chat messages.
            Returns the most semantically similar past messages to the given query.
            Prefer this tool over raw SQL when looking up content by meaning.
            """)
    public String searchSimilarMessages(
            @ToolParam(description = "Natural-language query describing what to look for")
            String query,
            @ToolParam(description = "Max rows to return (1..100, default 5)", required = false)
            Integer limit) {

        int safeLimit = (limit == null) ? 5 : Math.min(Math.max(limit, 1), MAX_ROWS);
        log.info("Tool searchSimilarMessages(query='{}', limit={})", query, safeLimit);

        List<ChatMessageEntity> rows = messageStore.search(query, safeLimit);
        if (rows.isEmpty()) return "(no matches)";

        String result = rows.stream()
                .map(m -> "[%s] %s: %s".formatted(
                        TS_FMT.format(m.getCreatedAt()), m.getAuthor(), m.getMessage()))
                .collect(Collectors.joining("\n"));
        return truncate(result);
    }

    // -------------------------------------------------------------------------
    // Generic SELECT escape hatch
    // -------------------------------------------------------------------------

    @Tool(description = """
            Execute a READ-ONLY SQL SELECT against the chat database and return the rows
            as plain text. Use this for aggregations or filters that semantic search
            cannot express (counting, grouping by author, time-range filtering, etc.).
            
            Schema:
              chat_message(id BIGINT, created_at TIMESTAMP, author TEXT, message TEXT, embedding vector(1536))
            
            Rules (enforced server-side – violations will be rejected):
              • Only a SINGLE SELECT statement is allowed
              • DML/DDL keywords (INSERT, UPDATE, DELETE, DROP, …) are forbidden
              • A LIMIT clause will be enforced (max 100 rows)
              • The 'embedding' column is huge – do NOT include it in the SELECT list
            """)
    public String executeSelectQuery(
            @ToolParam(description = "A single read-only SQL SELECT statement")
            String sql) {

        log.info("Tool executeSelectQuery: {}", sql);
        String safeSql;
        try {
            safeSql = sanitize(sql);
        } catch (IllegalArgumentException e) {
            return "ERROR: " + e.getMessage();
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.execute(
                    (java.sql.Connection con) -> {
                        try (var stmt = con.createStatement()) {
                            stmt.setQueryTimeout(STATEMENT_TIMEOUT_MS / 1000);
                            stmt.setMaxRows(MAX_ROWS);
                            try (var rs = stmt.executeQuery(safeSql)) {
                                return readResultSet(rs);
                            }
                        }
                    });
            if (rows.isEmpty()) return "(0 rows)";
            return truncate(formatRows(rows));
        } catch (Exception e) {
            log.warn("Tool SQL failed: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Validate + normalise the SQL. Throws if it violates the policy. */
    static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("SQL must not be empty");
        }
        String sql = raw.trim();
        // strip trailing semicolon(s)
        while (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).trim();

        if (sql.contains(";")) {
            throw new IllegalArgumentException("Only a single statement is allowed");
        }
        String lower = sql.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            throw new IllegalArgumentException("Only SELECT / WITH queries are allowed");
        }
        if (FORBIDDEN.matcher(lower).find()) {
            throw new IllegalArgumentException("Query contains a forbidden keyword");
        }
        // enforce row cap
        if (!lower.contains(" limit ")) {
            sql = sql + " LIMIT " + MAX_ROWS;
        }
        return sql;
    }

    private static List<Map<String, Object>> readResultSet(java.sql.ResultSet rs) throws java.sql.SQLException {
        var meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        while (rs.next()) {
            var row = new java.util.LinkedHashMap<String, Object>(cols);
            for (int i = 1; i <= cols; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            out.add(row);
        }
        return out;
    }

    private static String formatRows(List<Map<String, Object>> rows) {
        var header = String.join(" | ", rows.get(0).keySet());
        var body = rows.stream()
                .map(r -> r.values().stream()
                        .map(v -> v == null ? "" : v.toString())
                        .collect(Collectors.joining(" | ")))
                .collect(Collectors.joining("\n"));
        return header + "\n" + body + "\n(" + rows.size() + " rows)";
    }

    private static String truncate(String s) {
        return s.length() <= MAX_CHARS
                ? s
                : s.substring(0, MAX_CHARS) + "\n…(truncated)";
    }
}

