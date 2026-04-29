package com.odyldzhon.bot.ai;

import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.persistence.entity.ChatMessageEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseToolsTest {

    @Mock
    private MessageStore messageStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Sanitizes SELECT by trimming trailing semicolons and enforcing a limit")
    void sanitize_selectWithoutLimit_appendsMaxLimit() {
        // Given
        String sql = "  SELECT author, message FROM chat_message;  ";

        // When
        String result = DatabaseTools.sanitize(sql);

        // Then
        assertThat(result).isEqualTo("SELECT author, message FROM chat_message LIMIT 100");
    }

    @Test
    @DisplayName("Sanitizes SELECT that already has a limit without adding another one")
    void sanitize_selectWithLimit_keepsExistingLimit() {
        // Given
        String sql = "SELECT author FROM chat_message LIMIT 5";

        // When
        String result = DatabaseTools.sanitize(sql);

        // Then
        assertThat(result).isEqualTo(sql);
    }

    @Test
    @DisplayName("Rejects multiple SQL statements")
    void sanitize_multipleStatements_throwsIllegalArgumentException() {
        // Given
        String sql = "SELECT * FROM chat_message; SELECT * FROM chat_message";

        // When
        Throwable thrown = catchThrowable(() -> DatabaseTools.sanitize(sql));

        // Then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only a single statement is allowed");
    }

    @Test
    @DisplayName("Rejects unsafe SQL keywords")
    void sanitize_forbiddenKeyword_throwsIllegalArgumentException() {
        // Given
        String sql = "SELECT * FROM chat_message WHERE message LIKE 'drop'";

        // When
        Throwable thrown = catchThrowable(() -> DatabaseTools.sanitize(sql));

        // Then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Query contains a forbidden keyword");
    }

    @Test
    @DisplayName("Reports validation errors from executeSelectQuery without touching JDBC")
    void executeSelectQuery_invalidSql_returnsErrorMessage() {
        // Given
        DatabaseTools tools = new DatabaseTools(messageStore, jdbcTemplate);

        // When
        String result = tools.executeSelectQuery("DELETE FROM chat_message");

        // Then
        assertThat(result).isEqualTo("ERROR: Only SELECT / WITH queries are allowed");
    }

    @Test
    @DisplayName("Formats semantic search rows and clamps a too-large limit")
    void searchSimilarMessages_limitTooLarge_formatsRowsAndClampsLimit() {
        // Given
        DatabaseTools tools = new DatabaseTools(messageStore, jdbcTemplate);
        Instant createdAt = Instant.parse("2026-04-28T10:15:30Z");
        ChatMessageEntity row = ChatMessageEntity.builder()
                .createdAt(createdAt)
                .author("odyld")
                .message("hello dude")
                .build();
        when(messageStore.search("hello", 100)).thenReturn(List.of(row));

        // When
        String result = tools.searchSimilarMessages("hello", 500);

        // Then
        assertThat(result)
                .contains("odyld: hello dude")
                .contains("2026-04-28");
        verify(messageStore).search("hello", 100);
    }

    @Test
    @DisplayName("Returns no matches marker when semantic search has no rows")
    void searchSimilarMessages_emptyRows_returnsNoMatches() {
        // Given
        DatabaseTools tools = new DatabaseTools(messageStore, jdbcTemplate);
        when(messageStore.search("nothing", 5)).thenReturn(List.of());

        // When
        String result = tools.searchSimilarMessages("nothing", null);

        // Then
        assertThat(result).isEqualTo("(no matches)");
        verify(messageStore).search("nothing", 5);
    }
}



