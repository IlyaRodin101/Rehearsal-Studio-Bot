package ru.urfu.echobot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.urfu.echobot.bot.TelegramBot;
import ru.urfu.echobot.service.BookingMessageProcessingService;
import ru.urfu.echobot.service.Database;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String token = requiredEnvironment("TELEGRAM_TOKEN");
        long ownerId = Long.parseLong(requiredEnvironment("OWNER_ID"));
        String databasePath = System.getenv("DATABASE_PATH");
        if (databasePath == null || databasePath.trim().isEmpty()) {
            databasePath = "database.db";
        }

        Database database = new Database(databasePath);
        database.initialize();
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        BookingMessageProcessingService messageProcessingService =
                new BookingMessageProcessingService(ownerId, database);
        botsApi.registerBot(new TelegramBot(token, messageProcessingService));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Не задана обязательная переменная окружения " + name);
        }
        return value.trim();
    }
}
