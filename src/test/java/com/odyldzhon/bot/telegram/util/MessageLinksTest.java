package com.odyldzhon.bot.telegram.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageLinksTest {

    @Test
    @DisplayName("Extracts plain url entity from message text")
    void extract_urlEntity() {
        // Given
        String text = "check this https://example.com please";
        Message m = new Message();
        m.setText(text);
        m.setEntities(List.of(urlEntity(text.indexOf("https"), "https://example.com".length())));

        // When
        List<String> links = MessageLinks.extract(m);

        // Then
        assertThat(links).containsExactly("https://example.com");
    }

    @Test
    @DisplayName("Extracts text_link entity url")
    void extract_textLinkEntity() {
        // Given
        Message m = new Message();
        m.setText("click here");
        MessageEntity entity = new MessageEntity("text_link", 6, 4);
        entity.setUrl("https://example.com/page");
        m.setEntities(List.of(entity));

        // When
        List<String> links = MessageLinks.extract(m);

        // Then
        assertThat(links).containsExactly("https://example.com/page");
    }

    @Test
    @DisplayName("Extracts links from caption entities of a photo message")
    void extract_captionEntities() {
        // Given
        String caption = "see https://foo.bar";
        Message m = new Message();
        m.setCaption(caption);
        m.setCaptionEntities(List.of(urlEntity(caption.indexOf("https"), "https://foo.bar".length())));

        // When
        List<String> links = MessageLinks.extract(m);

        // Then
        assertThat(links).containsExactly("https://foo.bar");
    }

    @Test
    @DisplayName("Deduplicates repeated links and preserves order")
    void extract_deduplicates() {
        // Given
        String text = "https://a.example https://b.example https://a.example";
        Message m = new Message();
        m.setText(text);
        m.setEntities(List.of(
                urlEntity(0, "https://a.example".length()),
                urlEntity(text.indexOf("https://b"), "https://b.example".length()),
                urlEntity(text.lastIndexOf("https://a"), "https://a.example".length())
        ));

        // When
        List<String> links = MessageLinks.extract(m);

        // Then
        assertThat(links).containsExactly("https://a.example", "https://b.example");
    }

    @Test
    @DisplayName("Ignores non-link entities such as bold or hashtag")
    void extract_ignoresNonLinkEntities() {
        // Given
        Message m = new Message();
        m.setText("#hello world");
        m.setEntities(List.of(new MessageEntity("hashtag", 0, 6)));

        // When
        List<String> links = MessageLinks.extract(m);

        // Then
        assertThat(links).isEmpty();
    }

    @Test
    @DisplayName("Returns empty list for null message or no entities")
    void extract_nullOrEmpty() {
        // Given / When / Then
        assertThat(MessageLinks.extract(null)).isEmpty();
        assertThat(MessageLinks.extract(new Message())).isEmpty();
    }

    @Test
    @DisplayName("appendLinks appends new links on separate lines and skips duplicates already present")
    void appendLinks_appendsLinks() {
        // Given
        String text = "look here https://a.example and https://b.example";
        Message m = new Message();
        m.setText(text);
        m.setEntities(List.of(
                urlEntity(text.indexOf("https://a"), "https://a.example".length()),
                urlEntity(text.indexOf("https://b"), "https://b.example".length())
        ));

        // When
        String result = MessageLinks.appendLinks(text, m);

        // Then: both links already in text, nothing appended
        assertThat(result).isEqualTo(text);
    }

    @Test
    @DisplayName("appendLinks appends text_link URLs that are not part of the visible text")
    void appendLinks_appendsTextLinkUrls() {
        // Given
        Message m = new Message();
        m.setText("click here");
        MessageEntity entity = new MessageEntity("text_link", 6, 4);
        entity.setUrl("https://example.com/page");
        m.setEntities(List.of(entity));

        // When
        String result = MessageLinks.appendLinks("click here", m);

        // Then
        assertThat(result).isEqualTo("click here\nhttps://example.com/page");
    }

    @Test
    @DisplayName("appendLinks returns input unchanged when no links present")
    void appendLinks_noLinks() {
        // Given
        Message m = new Message();
        m.setText("plain text");

        // When
        String result = MessageLinks.appendLinks("plain text", m);

        // Then
        assertThat(result).isEqualTo("plain text");
    }

    private static MessageEntity urlEntity(int offset, int length) {
        return new MessageEntity("url", offset, length);
    }
}

