package com.odyldzhon.bot.configuration;

import com.odyldzhon.bot.telegram.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Registers the bot with Telegram's long-polling API once the Spring
 * application context is fully started.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotRegistrar implements ApplicationRunner {

    private final TelegramBot bot;

    @Override
    public void run(ApplicationArguments args) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);
        log.info("✅ Telegram bot registered and polling for messages…");
    }
}
