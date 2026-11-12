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

/// Собирает зависимости приложения и запускает Telegram-бота.
final class Application {
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
    /// Разделитель списка владельцев.
    private static final String OWNER_SEPARATOR = ",";

    /// Журнал приложения.
    private final Logger logger = LoggerFactory.getLogger(Application.class);

    /// Запускает приложение с настройками из окружения и файла `.env`.
    void run() {
        try {
            Map<String, String> configuration = loadConfiguration();
            String token = configuration.get(TELEGRAM_TOKEN);
            if (token == null || token.isBlank()) {
                logger.error("Укажите {} в .env или переменной окружения", TELEGRAM_TOKEN);
                return;
            }
            SqliteDatabase database = new SqliteDatabase(databasePath(configuration));
            MessageProcessingService service = createService(database, configuration);
            try (TelegramBot bot = new TelegramBot(token, service)) {
                bot.start();
            }
        } catch (Exception exception) {
            logger.error("Не удалось запустить Telegram-бота", exception);
        }
    }

    /// Создаёт сервис обработки сообщений.
    ///
    /// @param database      база данных SQLite
    /// @param configuration настройки приложения
    /// @return сервис обработки сообщений
    private MessageProcessingService createService(SqliteDatabase database, Map<String, String> configuration) {
        return new BookingMessageProcessingService(
                new SqliteBaseRepository(database),
                new SqliteRoomRepository(database),
                new SqliteBookingRepository(database),
                new BookingRules(Clock.systemUTC()),
                parseOwnerIds(configuration.get(OWNER_IDS)));
    }

    /// Возвращает путь к файлу базы данных.
    ///
    /// @param configuration настройки приложения
    /// @return путь к файлу базы данных
    private String databasePath(Map<String, String> configuration) {
        String path = configuration.get(DATABASE_PATH);
        return path == null || path.isBlank() ? DEFAULT_DATABASE_PATH : path;
    }

    /// Читает переменные окружения и дополняет их значениями из `.env`.
    ///
    /// @return настройки приложения
    /// @throws IOException если файл `.env` не удалось прочитать
    private Map<String, String> loadConfiguration() throws IOException {
        Map<String, String> configuration = new HashMap<>(System.getenv());
        Path envFile = Path.of(ENV_FILE);
        if (Files.isRegularFile(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                readEntry(configuration, line);
            }
        }
        return Map.copyOf(configuration);
    }

    /// Добавляет одну строку файла `.env`, если переменная ещё не задана.
    ///
    /// @param configuration настройки приложения
    /// @param line          строка файла `.env`
    private void readEntry(Map<String, String> configuration, String line) {
        String entry = line.trim();
        if (!entry.isEmpty() && !entry.startsWith("#")) {
            int separator = entry.indexOf('=');
            if (separator > 0) {
                String key = entry.substring(0, separator).trim();
                String current = configuration.get(key);
                if (current == null || current.isBlank()) {
                    configuration.put(key, unquote(entry.substring(separator + 1).trim()));
                }
            }
        }
    }

    /// Убирает кавычки вокруг значения из `.env`.
    ///
    /// @param value значение из файла
    /// @return значение без кавычек
    private String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /// Разбирает список Telegram ID владельцев.
    ///
    /// @param value строка со списком идентификаторов через запятую
    /// @return неизменяемое множество идентификаторов
    private Set<Long> parseOwnerIds(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        try {
            return Arrays.stream(value.split(OWNER_SEPARATOR))
                    .map(String::trim)
                    .filter(ownerId -> !ownerId.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("OWNER_IDS должен быть списком Telegram ID через запятую",
                    exception);
        }
    }
}
