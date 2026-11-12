package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.bot.DummyBot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.SqliteBaseRepository;
import ru.urfu.echobot.repository.SqliteBookingRepository;
import ru.urfu.echobot.repository.SqliteDatabase;
import ru.urfu.echobot.repository.SqliteRoomRepository;

/// Проверяет платную и бесплатную отмену записей и уведомление владельца.
final class BookingCancellationRulesTest {
    /// Клиент.
    private static final long CLIENT = 42L;
    /// Владелец тестовой базы.
    private static final long OWNER = 101L;

    /// Бот-заглушка.
    private DummyBot bot;
    /// Проверяемый сервис.
    private BookingMessageProcessingService service;
    /// Хранилище баз.
    private SqliteBaseRepository baseRepository;
    /// Хранилище броней.
    private SqliteBookingRepository bookingRepository;
    @TempDir
    private Path temporaryDirectory;

    /// Создаёт базу «Центр» с залом и фиксированными часами.
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
        baseRepository = new SqliteBaseRepository(database);
        bookingRepository = new SqliteBookingRepository(database);
        SqliteRoomRepository roomRepository = new SqliteRoomRepository(database);
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day != DayOfWeek.SUNDAY) {
                schedule.put(day, new OpeningHours(9, 24));
            }
        }
        Base center = baseRepository.save(new Base(0, OWNER, "Центр", "ул. Ленина, 1",
                "Europe/Moscow", 2, "Барабаны", schedule));
        roomRepository.save(new Room(0, center.id(), "Зал 1", false));
        service = new BookingMessageProcessingService(baseRepository, roomRepository, bookingRepository,
                new BookingRules(clock));
        bot = new DummyBot();
    }

    /// Given: до записи меньше 48 часов. When: клиент отменяет. Then: видит неустойку и владелец получает уведомление.
    @Test
    void lateCancellationWarnsAboutPenaltyAndNotifiesOwner() {
        chooseSlot("2026-10-21", 18);
        type("Анна");
        type("+7 999 123-45-67");
        press("book:skip");
        assertTrue(lastText().startsWith("Это время начинается меньше чем через 48 часов"));

        press("book:confirm");
        press("menu:mine");
        press(callback("mine:cancel:"));
        assertTrue(lastText().contains("До начала меньше 48 часов, отмена платная (100% стоимости)."));
        press(callback("mine:cancelyes:"));

        assertTrue(bot.messages().stream().anyMatch(message -> message.text() != null
                && message.text().startsWith("Клиент отменил запись с неустойкой 100%:")));
        assertTrue(bot.messages().stream().anyMatch(message -> message.text() != null
                && message.text().contains("Анна") && message.text().contains("+79991234567")));
        assertTrue(bookingRepository.findByUser(CLIENT).isEmpty());
    }

    /// Given: до записи больше 48 часов. When: клиент отменяет. Then: отмена бесплатна и время снова доступно.
    @Test
    void earlyCancellationIsFreeAndNotifiesOwner() {
        chooseSlot("2026-10-24", 18);
        type("Игорь");
        type("+7 912 000-11-22");
        press("book:skip");
        assertFalse(lastText().contains("отмена записи будет платной"));

        press("book:confirm");
        press("menu:mine");
        press(callback("mine:cancel:"));
        assertTrue(lastText().contains("До начала больше 48 часов, отмена без неустойки."));
        press(callback("mine:cancelyes:"));

        assertTrue(bot.messages().stream().anyMatch(message -> message.text() != null
                && message.text().startsWith("Клиент отменил запись бесплатно:")));
        assertTrue(bot.messages().stream().anyMatch(message -> "Время снова доступно для брони."
                .equals(message.text())));
    }

    /// Given: у базы срок 24 часа и неустойка 50%. When: клиент отменяет. Then: используются значения базы.
    @Test
    void cancellationUsesRulesConfiguredForBase() {
        Base base = baseRepository.findById(1).orElseThrow();
        baseRepository.update(base.withCancellationRules(24, 50));
        chooseSlot("2026-10-20", 18);
        type("Олег");
        type("+7 903 555-66-77");
        press("book:skip");
        assertTrue(lastText().contains("меньше чем через 24 часов"));
        assertTrue(lastText().contains("50% стоимости"));

        press("book:confirm");
        press("menu:mine");
        press(callback("mine:cancel:"));
        assertTrue(lastText().contains("До начала меньше 24 часов, отмена платная (50% стоимости)."));
        press(callback("mine:cancelyes:"));
        assertTrue(bot.messages().stream().anyMatch(message -> message.text() != null
                && message.text().startsWith("Клиент отменил запись с неустойкой 50%:")));
    }

    /// Проходит шаги бронирования до подтверждения.
    ///
    /// @param date дата в формате ГГГГ-ММ-ДД
    /// @param hour час начала
    private void chooseSlot(String date, int hour) {
        press("menu:book");
        press("book:base:1");
        press("book:room:1");
        press("book:day:" + date);
        press("book:time:" + date + ":" + hour);
        press("book:hours:2");
    }

    /// Нажимает кнопку клиента.
    ///
    /// @param data callback-данные кнопки
    private void press(String data) {
        service.processMessage(new Message(null, data, List.of()), CLIENT, bot);
    }

    /// Отправляет текст клиента.
    ///
    /// @param text текст сообщения
    private void type(String text) {
        service.processMessage(new Message(text, null, List.of()), CLIENT, bot);
    }

    /// Возвращает callback последней кнопки с указанным началом.
    ///
    /// @param prefix начало callback-данных
    /// @return callback-данные кнопки
    private String callback(String prefix) {
        return bot.lastMessage().keyboard().stream()
                .flatMap(List::stream)
                .map(Button::data)
                .filter(data -> data.startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }

    /// Возвращает текст последнего сообщения бота.
    ///
    /// @return текст сообщения
    private String lastText() {
        return bot.lastMessage().text();
    }
}
