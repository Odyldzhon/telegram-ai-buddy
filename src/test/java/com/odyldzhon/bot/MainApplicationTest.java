package com.odyldzhon.bot;

import com.odyldzhon.bot.configuration.BotRegistrar;
import com.odyldzhon.bot.configuration.ChatClientConfig;
import com.odyldzhon.bot.persistence.MessageStore;
import com.odyldzhon.bot.telegram.TelegramBot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
@ActiveProfiles("test")
class MainApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private BotRegistrar botRegistrar;

    @MockitoBean
    private MessageStore messageStore;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean(name = ChatClientConfig.ASSISTANT)
    private ChatClient assistantChatClient;

    @MockitoBean(name = ChatClientConfig.IMAGE)
    private ChatClient imageChatClient;

    @Test
    @DisplayName("Starts the Spring application context with external integrations mocked")
    void applicationContext_mockedIntegrations_loadsSuccessfully() {
        // Given
        assertThat(applicationContext).isNotNull();

        // When
        TelegramBot telegramBot = applicationContext.getBean(TelegramBot.class);

        // Then
        assertThat(telegramBot).isNotNull();
        assertThat(applicationContext.getBean(ChatClientConfig.ASSISTANT, ChatClient.class)).isSameAs(assistantChatClient);
        assertThat(applicationContext.getBean(ChatClientConfig.IMAGE, ChatClient.class)).isSameAs(imageChatClient);
    }
}



