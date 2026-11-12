package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.bot.DummyBot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.SqliteBaseRepository;
import ru.urfu.echobot.repository.SqliteBookingRepository;
import ru.urfu.echobot.repository.SqliteDatabase;
import ru.urfu.echobot.repository.SqliteRoomRepository;
import ru.urfu.echobot.repository.SqliteSubscriptionRepository;

/// Сквозные тесты раздела «Горячие слоты».
final class HotSlotFlowTest {
    /// Клиент.
    private static final long CLIENT = 42L;
    /// Владелец базы.
    private static final long OWNER = 50L;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
    @TempDir
    private Path temporaryDirectory;
    private SqliteBookingRepository bookingRepository;
    private SqliteSubscriptionRepository subscriptions;
    private BookingMessageProcessingService service;
    private DummyBot bot;
    private Base base;
    private Room room;

    /// Создаёт базу, зал и сервис обработки сообщений.
    @BeforeEach
    void setUp() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
        SqliteBaseRepository baseRepository = new SqliteBaseRepository(database);
        SqliteRoomRepository roomRepository = new SqliteRoomRepository(database);
        bookingRepository = new SqliteBookingRepository(database);
        subscriptions = new SqliteSubscriptionRepository(database);
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            schedule.put(day, new OpeningHours(9, 24));
        }
        base = baseRepository.save(new Base(0, OWNER, "Центр", "Ленина, 1", "Europe/Moscow", 2, "",
                schedule, 48, 100));
        room = roomRepository.save(new Room(0, base.id(), "Зал 1", false));
        service = new BookingMessageProcessingService(baseRepository, roomRepository, bookingRepository,
                new BookingRules(clock), Set.of(OWNER), subscriptions);
        bot = new DummyBot();
    }

    /// Given: у базы есть свободное время. When: клиент открывает «Горячие слоты» и бронирует слот.
    /// Then: создаётся обычная запись на выбранное время.
    @Test
    void clientBooksSlotFromHotSlotsList() {
        press(CLIENT, "menu:hot");
        assertTrue(lastText().startsWith("Выберите базу:"));
        assertTrue(lastButtons().contains("Центр"));

        press(CLIENT, "hot:base:" + base.id());
        assertTrue(lastText().contains("«Горячие» слоты базы «Центр»:"));
        String slotCallback = lastButtonsData().stream()
                .filter(data -> data.startsWith("hot:book:")).findFirst().orElseThrow();
        LocalDateTime start = LocalDateTime.parse(slotCallback.split(":")[3],
                DateTimeFormatter.ofPattern("uuuuMMddHHmm"));
        assertTrue(lastButtons().stream().anyMatch(text -> text.startsWith("Зал 1, сегодня")));

        press(CLIENT, slotCallback);
        press(CLIENT, "book:hours:2");
        type(CLIENT, "Анна");
        type(CLIENT, "+7 999 123-45-67");
        press(CLIENT, "book:skip");
        press(CLIENT, "book:confirm");

        List<Booking> created = bookingRepository.findByUser(CLIENT);
        assertEquals(1, created.size());
        assertEquals(start, created.getFirst().start());
        assertEquals(2, created.getFirst().hours());
    }

    /// Given: подписок нет. When: клиент открывает «Подписку». Then: базы показываются без отметок.
    @Test
    void subscriptionListShowsBasesWithoutMarks() {
        press(CLIENT, "menu:sub");

        assertTrue(lastText().startsWith("Выберите базы, о «горячих» слотах"));
        assertTrue(lastButtons().contains("Центр"));
        assertTrue(lastButtons().stream().noneMatch(text -> text.startsWith("✓")));
    }

    /// Отправляет сервису нажатие кнопки.
    ///
    /// @param user клиент
    /// @param data данные кнопки
    private void press(long user, String data) {
        service.processMessage(new Message(null, data, List.of()), user, bot);
    }

    /// Отправляет сервису текст.
    ///
    /// @param user клиент
    /// @param text текст сообщения
    private void type(long user, String text) {
        service.processMessage(new Message(text, null, List.of()), user, bot);
    }

    /// Возвращает подписи кнопок последнего сообщения.
    ///
    /// @return подписи кнопок
    private List<String> lastButtons() {
        return bot.lastMessage().keyboard().stream().flatMap(List::stream).map(Button::text).toList();
    }

    /// Возвращает callback-данные кнопок последнего сообщения.
    ///
    /// @return данные кнопок
    private List<String> lastButtonsData() {
        return bot.lastMessage().keyboard().stream().flatMap(List::stream).map(Button::data).toList();
    }

    /// Возвращает текст последнего сообщения.
    ///
    /// @return текст сообщения
    private String lastText() {
        return bot.lastMessage().text();
    }
}
