package com.odyldzhon.bot.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageDescriberTest {

    @Mock
    private ChatClient imageChatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Test
    @DisplayName("Prefixes successful image descriptions for storage")
    void describe_validImage_returnsPrefixedDescription() {
        // Given
        ImageDescriber describer = new ImageDescriber(imageChatClient);
        when(imageChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("A bowling ball on a rug.");

        // When
        String result = describer.describe(new byte[]{1, 2, 3}, "image/jpeg", "nice rug");

        // Then
        assertThat(result).isEqualTo("image: A bowling ball on a rug.");
        verify(imageChatClient).prompt();
    }

    @Test
    @DisplayName("Uses JPEG as the default MIME type when none is provided")
    void describe_nullMimeType_returnsPrefixedDescription() {
        // Given
        ImageDescriber describer = new ImageDescriber(imageChatClient);
        when(imageChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("A quiet scene.");

        // When
        String result = describer.describe(new byte[]{9}, null, null);

        // Then
        assertThat(result).isEqualTo("image: A quiet scene.");
    }

    @Test
    @DisplayName("Returns null when image description fails before the AI call")
    void describe_invalidMimeType_returnsNull() {
        // Given
        ImageDescriber describer = new ImageDescriber(imageChatClient);

        // When
        String result = describer.describe(new byte[]{1}, "not a mime type", "caption");

        // Then
        assertThat(result).isNull();
        verify(imageChatClient, never()).prompt();
    }
}


