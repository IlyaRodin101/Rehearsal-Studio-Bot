package ru.urfu.echobot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.urfu.echobot.bot.TelegramBot;
import ru.urfu.echobot.repository.SqliteBaseRepository;
import ru.urfu.echobot.repository.SqliteBookingRepository;
import ru.urfu.echobot.repository.SqliteDatabase;
import ru.urfu.echobot.repository.SqliteRoomRepository;
import ru.urfu.echobot.service.BookingMessageProcessingService;
import ru.urfu.echobot.service.BookingRules;
import ru.urfu.echobot.service.MessageProcessingService;

/// Основной класс для запуска приложения.
final public class Main {
    /// Логгер приложения.
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    /// Имя переменной окружения с токеном бота.
    private static final String TELEGRAM_TOKEN = "TELEGRAM_TOKEN";
    /// Имя переменной окружения со списком Telegram ID владельцев.
    private static final String OWNER_IDS = "OWNER_IDS";
    /// Путь к файлу SQLite по умолчанию.
    private static final String DEFAULT_DATABASE_PATH = "bookings.db";
    /// Имя переменной окружения с расположением файла базы данных.
    private static final String DATABASE_PATH = "DATABASE_PATH";
    /// Имя файла с локальными настройками запуска.
    private static final String ENV_FILE = ".env";

    /// Приватный конструктор, добавленный для того, чтобы явно сообщить,
    /// что не нужно создавать объекты этого класса.
    private Main() {
    }

    /// Точка входа в программу.
    static void main() {
        try {
            final Map<String, String> configuration = loadConfiguration();
            final String tgBotToken = configuration.get(TELEGRAM_TOKEN);
            if (tgBotToken == null || tgBotToken.isBlank()) {
                LOGGER.error("Set {} in .env or as an environment variable", TELEGRAM_TOKEN);
                return;
            }
            final Clock clock = Clock.systemUTC();
            final String databasePath = configuration.get(DATABASE_PATH);
            final SqliteDatabase database = new SqliteDatabase(
                    databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath);
            final Set<Long> owners = parseOwnerIds(configuration.get(OWNER_IDS));
            final MessageProcessingService service = new BookingMessageProcessingService(
                    new SqliteBaseRepository(database),
                    new SqliteRoomRepository(database),
                    new SqliteBookingRepository(database),
                    new BookingRules(clock), owners);
            try (TelegramBot bot = new TelegramBot(tgBotToken, service)) {
                bot.start();
            }
        } catch (Exception exception) {
            LOGGER.error("Couldn't start Telegram bot", exception);
        }
    }

    private static Map<String, String> loadConfiguration() throws IOException {
        Map<String, String> configuration = new HashMap<>(System.getenv());
        Path envFile = Path.of(ENV_FILE);
        if (Files.isRegularFile(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                String entry = line.trim();
                if (entry.isEmpty() || entry.startsWith("#")) {
                    continue;
                }
                int separator = entry.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = entry.substring(0, separator).trim();
                String value = unquote(entry.substring(separator + 1).trim());
                if (configuration.get(key) == null || configuration.get(key).isBlank()) {
                    configuration.put(key, value);
                }
            }
        }
        return Map.copyOf(configuration);
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Set<Long> parseOwnerIds(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        try {
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(ownerId -> !ownerId.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("OWNER_IDS must be a comma-separated list of Telegram IDs",
                    exception);
        }
    }
}
