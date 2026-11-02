package ru.urfu.echobot.service;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.bot.DummyBot;
import ru.urfu.echobot.repository.InMemoryBaseRepository;
import ru.urfu.echobot.repository.InMemoryRoomRepository;
import ru.urfu.echobot.repository.InitialData;
import ru.urfu.echobot.repository.SqliteBookingRepository;

/// Проверяет, что подтверждённая клиентом бронь доступна после перезапуска сервиса и репозитория.
final class BookingPersistenceIntegrationTest {
    /// Идентификатор клиента, создавшего бронь.
    private static final long CLIENT_ID = 42L;
    /// Временный каталог для файла SQLite.
    @TempDir
    private Path temporaryDirectory;

    /// Проверяет полный сценарий бронирования и повторное чтение записи после повторного подключения к SQLite.
    @Test
    void confirmedBookingRemainsVisibleAfterRestart() throws Exception {
        Path databasePath = temporaryDirectory.resolve("booking-persistence.db");
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
        InitialData initialData = new InitialData(clock);
        DummyBot firstBot = new DummyBot();

        try (SqliteBookingRepository repository = new SqliteBookingRepository(databasePath)) {
            repository.refreshInitialBookings(initialData.bookings());
            BookingMessageProcessingService service = new BookingMessageProcessingService(
                    new InMemoryBaseRepository(initialData.bases()),
                    new InMemoryRoomRepository(initialData.rooms()), repository, new BookingRules(clock));
            sendCallback(service, firstBot, "menu:book");
            pressButton(service, firstBot, "Центр");
            pressButton(service, firstBot, "Зал 1");
            pressButton(service, firstBot, "Октябрь 2026");
            pressButtonByData(service, firstBot, "book:week:2026-10-19");
            pressButtonByData(service, firstBot, "book:day:2026-10-24");
            pressButton(service, firstBot, "18:00");
            pressButton(service, firstBot, "2");
            sendText(service, firstBot, "Анна");
            sendText(service, firstBot, "+7 999 123-45-67");
            pressButton(service, firstBot, "Пропустить");
            pressButton(service, firstBot, "Подтвердить");

            org.junit.jupiter.api.Assertions.assertTrue(firstBot.messages().stream()
                    .anyMatch(message -> "Готово, запись создана.".equals(message.text())));
        }

        try (SqliteBookingRepository repository = new SqliteBookingRepository(databasePath)) {
            repository.refreshInitialBookings(initialData.bookings());
            BookingMessageProcessingService restartedService = new BookingMessageProcessingService(
                    new InMemoryBaseRepository(initialData.bases()),
                    new InMemoryRoomRepository(initialData.rooms()), repository, new BookingRules(clock));
            DummyBot restartedBot = new DummyBot();

            sendCallback(restartedService, restartedBot, "menu:mine");

            org.junit.jupiter.api.Assertions.assertTrue(restartedBot.messages().stream()
                    .anyMatch(message -> message.text() != null
                            && message.text().contains("Центр, Зал 1")
                            && message.text().contains("18:00-20:00")));
            org.junit.jupiter.api.Assertions.assertEquals(1, repository.findByUser(CLIENT_ID).size());
            org.junit.jupiter.api.Assertions.assertEquals(2, repository.findByUser(0L).size());
        }
    }

    /// Отправляет нажатие кнопки в сервис.
    ///
    /// @param service сервис обработки сообщений
    /// @param bot бот-заглушка
    /// @param callback callback-данные кнопки
    private void sendCallback(BookingMessageProcessingService service, DummyBot bot, String callback) {
        service.processMessage(new Message(null, callback, List.of()), CLIENT_ID, bot);
    }

    /// Отправляет текст в сервис.
    ///
    /// @param service сервис обработки сообщений
    /// @param bot бот-заглушка
    /// @param text текст пользователя
    private void sendText(BookingMessageProcessingService service, DummyBot bot, String text) {
        service.processMessage(new Message(text, null, List.of()), CLIENT_ID, bot);
    }

    /// Нажимает кнопку по отображаемой подписи в последней клавиатуре.
    ///
    /// @param service сервис обработки сообщений
    /// @param bot бот-заглушка
    /// @param label подпись кнопки
    private void pressButton(BookingMessageProcessingService service, DummyBot bot, String label) {
        String callback = bot.lastMessage().keyboard().stream()
                .flatMap(List::stream)
                .filter(button -> label.equals(button.text()))
                .map(Button::data)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Кнопка не найдена: " + label));
        sendCallback(service, bot, callback);
    }

    /// Нажимает кнопку по точному callback-коду в последней клавиатуре.
    ///
    /// @param service сервис обработки сообщений
    /// @param bot бот-заглушка
    /// @param callback требуемые callback-данные
    private void pressButtonByData(BookingMessageProcessingService service, DummyBot bot, String callback) {
        String foundCallback = bot.lastMessage().keyboard().stream()
                .flatMap(List::stream)
                .map(Button::data)
                .filter(callback::equals)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Кнопка с callback не найдена: " + callback));
        sendCallback(service, bot, foundCallback);
    }
}
