package ru.urfu.echobot;

import java.nio.file.Path;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.urfu.echobot.bot.TelegramBot;
import ru.urfu.echobot.repository.InMemoryBaseRepository;
import ru.urfu.echobot.repository.InMemoryRoomRepository;
import ru.urfu.echobot.repository.InitialData;
import ru.urfu.echobot.repository.SqliteBookingRepository;
import ru.urfu.echobot.service.BookingMessageProcessingService;
import ru.urfu.echobot.service.BookingRules;
import ru.urfu.echobot.service.MessageProcessingService;

/// Основной класс для запуска приложения.
public final class Main {
    /// Имя переменной окружения с токеном бота.
    private static final String TELEGRAM_TOKEN = "TELEGRAM_TOKEN";
    /// Имя переменной окружения с путём к файлу SQLite.
    private static final String DATABASE_PATH = "DATABASE_PATH";
    /// Путь к файлу SQLite по умолчанию.
    private static final String DEFAULT_DATABASE_PATH = "database.db";

    /// Приватный конструктор, добавленный для того, чтобы явно сообщить,
    /// что не нужно создавать объекты этого класса.
    private Main() {
    }

    /// Точка входа в программу.
    static void main() {
        final Logger logger = LoggerFactory.getLogger(Main.class);
        final String tgBotToken = System.getenv(TELEGRAM_TOKEN);
        if (tgBotToken == null) {
            logger.error("Set environment variable {}", TELEGRAM_TOKEN);
            return;
        }
        final Clock clock = Clock.systemUTC();
        final InitialData data = new InitialData(clock);
        final String databasePath = System.getenv().getOrDefault(DATABASE_PATH, DEFAULT_DATABASE_PATH);
        try (SqliteBookingRepository bookingRepository = new SqliteBookingRepository(Path.of(databasePath))) {
            bookingRepository.refreshInitialBookings(data.bookings());
            final MessageProcessingService service = new BookingMessageProcessingService(
                    new InMemoryBaseRepository(data.bases()),
                    new InMemoryRoomRepository(data.rooms()),
                    bookingRepository,
                    new BookingRules(clock));
            try (TelegramBot bot = new TelegramBot(tgBotToken, service)) {
                bot.start();
            }
        } catch (Exception e) {
            logger.error("Couldn't start Telegram bot", e);
        }
    }
}
