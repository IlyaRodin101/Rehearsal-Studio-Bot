package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.bot.DummyBot;

/// Тесты диалогов бронирования без Telegram.
final class BookingMessageProcessingServiceTest {
    /// Идентификатор владельца в тестах.
    private static final long OWNER_ID = 1L;
    /// Идентификатор обычного клиента в тестах.
    private static final long CLIENT_ID = 2L;

    /// Временная папка для файла базы данных.
    @TempDir
    Path tempDir;
    /// Проверяемый сервис.
    private BookingMessageProcessingService service;
    /// Бот-заглушка, собирающая ответы.
    private DummyBot bot;

    /// Создаёт чистую базу, сервис и бота перед каждым тестом.
    ///
    /// @throws Exception если не удалось создать базу
    @BeforeEach
    void setUp() throws Exception {
        Database database = new Database(tempDir.resolve("test.db").toString());
        database.initialize();
        service = new BookingMessageProcessingService(OWNER_ID, database);
        bot = new DummyBot();
    }

    /// Given: пишет любой пользователь. When: отправляет /start. Then: в меню три кнопки.
    @Test
    void startShowsMainMenuButtons() {
        service.processMessage(Message.builder().text("/start").build(), CLIENT_ID, bot);

        assertEquals(List.of("Забронировать", "Мои записи", "О базе"), buttonTexts(bot.lastMessage()));
    }

    /// Собирает подписи всех кнопок сообщения в один список.
    ///
    /// @param message сообщение с клавиатурой
    /// @return подписи кнопок
    private List<String> buttonTexts(Message message) {
        return message.keyboard().stream()
                .flatMap(List::stream)
                .map(Button::text)
                .toList();
    }
}