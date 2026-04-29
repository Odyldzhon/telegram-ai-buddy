package com.odyldzhon.bot.ai;

import com.odyldzhon.bot.configuration.ChatClientConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * Sends an image (with optional caption) to Gemini and returns
 * a short, factual textual description suitable for storage and
 * later semantic search.
 *
 * The {@link ChatClient} (with the image-specific system prompt and no tools)
 * is provided by {@link ChatClientConfig#imageChatClient}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ImageDescriber {

    /** Prefix used for stored auto-generated image descriptions. */
    private static final String IMAGE_PREFIX = "image: ";

    /** Field/parameter name matches bean name {@link ChatClientConfig#IMAGE}. */
    private final ChatClient imageChatClient;

    /**
     * @param bytes    raw image bytes
     * @param mimeType e.g. "image/jpeg"
     * @param caption  optional Telegram caption (may be null/blank)
     * @return short textual description prefixed with {@value #IMAGE_PREFIX},
     *         or {@code null} if the call failed
     */
    public String describe(byte[] bytes, String mimeType, String caption) {
        try {
            MimeType mt = mimeType == null ? MimeTypeUtils.IMAGE_JPEG : MimeTypeUtils.parseMimeType(mimeType);
            Media media = Media.builder()
                    .mimeType(mt)
                    .data(new ByteArrayResource(bytes))
                    .build();

            String userText = (caption == null || caption.isBlank())
                    ? "Describe this image."
                    : "Describe this image. User sign: " + caption;

            return IMAGE_PREFIX + imageChatClient.prompt()
                    .user(u -> u.text(userText).media(media))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Image description failed: {}", e.getMessage(), e);
            return null;
        }
    }
}

