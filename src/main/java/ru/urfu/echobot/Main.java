package ru.urfu.echobot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.urfu.echobot.bot.TelegramBot;
import ru.urfu.echobot.service.BookingMessageProcessingService;
import ru.urfu.echobot.service.Database;
import ru.urfu.echobot.service.MessageProcessingService;

/// Основной класс для запуска приложения.
final public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final String TELEGRAM_TOKEN = "TELEGRAM_TOKEN";
    private static final String DATABASE_PATH = "DATABASE_PATH";
    private static final String DEFAULT_DATABASE_PATH = "database.db";

    /// Приватный конструктор, добавленный для того, чтобы явно сообщить,
    /// что не нужно создавать объекты этого класса.
    private Main() {
    }

    /// Точка входа в программу.
    static void main() {
        final String tgBotToken = System.getenv(TELEGRAM_TOKEN);
        if (tgBotToken == null) {
            LOGGER.error("Set environment variable {}", TELEGRAM_TOKEN);
            return;
        }
        final String databasePath = System.getenv(DATABASE_PATH) == null || System.getenv(DATABASE_PATH).isBlank() ? DEFAULT_DATABASE_PATH : System.getenv(DATABASE_PATH).trim();

        try {
            final Database database = new Database(databasePath);
            database.initialize();
            final MessageProcessingService service = new BookingMessageProcessingService(database);
            try (TelegramBot bot = new TelegramBot(tgBotToken, service)) {
                bot.start();
            }
        } catch (Exception e) {
            LOGGER.error("Couldn't start Telegram bot", e);
        }
    }
}
