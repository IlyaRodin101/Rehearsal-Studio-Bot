package ru.urfu.echobot;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.urfu.echobot.bot.TelegramBot;
import ru.urfu.echobot.repository.InMemoryBaseRepository;
import ru.urfu.echobot.repository.InMemoryBookingRepository;
import ru.urfu.echobot.repository.InMemoryRoomRepository;
import ru.urfu.echobot.repository.InitialData;
import ru.urfu.echobot.service.BookingMessageProcessingService;
import ru.urfu.echobot.service.BookingRules;
import ru.urfu.echobot.service.MessageProcessingService;

/// Основной класс для запуска приложения.
final public class Main {
    /// Логгер приложения.
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    /// Имя переменной окружения с токеном бота.
    private static final String TELEGRAM_TOKEN = "TELEGRAM_TOKEN";

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
        final Clock clock = Clock.systemUTC();
        final InitialData data = new InitialData(clock);
        final MessageProcessingService service = new BookingMessageProcessingService(
                new InMemoryBaseRepository(data.bases()),
                new InMemoryRoomRepository(data.rooms()),
                new InMemoryBookingRepository(data.bookings()),
                new BookingRules(clock));
        try (TelegramBot bot = new TelegramBot(tgBotToken, service)) {
            bot.start();
        } catch (Exception e) {
            LOGGER.error("Couldn't start Telegram bot", e);
        }
    }
}
