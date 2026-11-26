package ru.urfu.echobot.service.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
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
import ru.urfu.echobot.service.Button;
import ru.urfu.echobot.service.Message;
import ru.urfu.echobot.service.UserDirectory;

/// Сквозные тесты раздела «Расписание» и отмены записей персоналом.
final class ScheduleFlowTest {
    /// Клиент, создающий записи.
    private static final long CLIENT = 42L;
    /// Администратор базы.
    private static final long ADMIN = 77L;
    /// Владелец базы.
    private static final long OWNER = 50L;
    /// Подписчик на «горячие» слоты.
    private static final long SUBSCRIBER = 43L;
    /// День тестовой записи.
    private static final LocalDate BOOKING_DATE = LocalDate.of(2026, 10, 21);

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
    private final DateTimeFormatter callbackFormat = DateTimeFormatter.ofPattern("uuuuMMddHHmm");
    @TempDir
    private Path temporaryDirectory;
    private SqliteBookingRepository bookings;
    private SqliteSubscriptionRepository subscriptions;
    private BookingMessageProcessingService service;
    private DummyBot bot;
    private Base base;
    private Room room;

    /// Создаёт базу, зал, администратора и сервис обработки сообщений.
    @BeforeEach
    void setUp() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
        SqliteBaseRepository bases = new SqliteBaseRepository(database);
        SqliteRoomRepository rooms = new SqliteRoomRepository(database);
        bookings = new SqliteBookingRepository(database);
        subscriptions = new SqliteSubscriptionRepository(database);
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            schedule.put(day, new OpeningHours(9, 24));
        }
        base = bases.save(new Base(0, OWNER, "Центр", "Ленина, 1", "Europe/Moscow", 2, "",
                schedule, 48, 100));
        room = rooms.save(new Room(0, base.id(), "Зал 1", false));
        UserDirectory directory = UserDirectory.inMemory();
        directory.remember(ADMIN, "anna_admin");
        directory.addAdministrator(directory.findByUsername("anna_admin").orElseThrow());
        service = new BookingMessageProcessingService(bases, rooms, bookings, new BookingRules(clock),
                Set.of(OWNER), subscriptions, directory);
        bot = new DummyBot();
    }

    /// Клиент не видит кнопку расписания в главном меню.
    @Test
    void clientDoesNotSeeScheduleButton() {
        type(CLIENT, "/start");

        assertFalse(lastButtons().contains("Расписание"));
    }

    /// Владелец видит кнопку расписания в главном меню.
    @Test
    void ownerSeesScheduleButton() {
        type(OWNER, "/start");

        assertTrue(lastButtons().contains("Расписание"));
    }

    /// Клиент не может открыть расписание поддельным callback.
    @Test
    void clientCannotOpenScheduleByForgedCallback() {
        press(CLIENT, "menu:schedule");
        assertEquals("Действие недоступно.", lastText());

        press(CLIENT, "schedule:base:" + base.id());
        assertEquals("Действие недоступно.", lastText());
    }

    /// Администратор видит кнопку и проходит цепочку база, месяц, неделя, день.
    @Test
    void administratorSeesScheduleAndNavigatesToDay() {
        addBooking("Нужен микрофон");
        type(ADMIN, "/start");
        assertTrue(lastButtons().contains("Расписание"));

        press(ADMIN, "menu:schedule");
        assertEquals("Выберите базу:", lastText());
        press(ADMIN, "schedule:base:" + base.id());
        assertEquals("Выберите месяц:", lastText());
        assertTrue(lastButtons().contains("Октябрь 2026"));
        press(ADMIN, "schedule:month:" + base.id() + ":2026-10");
        assertEquals("Выберите неделю:", lastText());
        press(ADMIN, "schedule:week:" + base.id() + ":2026-10-19");
        assertEquals("Выберите день:", lastText());
        assertTrue(lastButtonsData().contains("schedule:day:" + base.id() + ":" + BOOKING_DATE));

        press(ADMIN, "schedule:day:" + base.id() + ":" + BOOKING_DATE);

        assertTrue(lastText().contains("Расписание базы «Центр»"));
        assertTrue(lastText().contains("Зал 1:"));
        assertTrue(lastText().contains("18:00-20:00, Анна, +79991234567, комм.: Нужен микрофон"));
    }

    /// Администратор отменяет чужую запись и уведомляет клиента и владельца.
    @Test
    void administratorCancelsBookingAndNotifiesClientAndOwner() {
        addBooking("Нужен микрофон");
        openDay(ADMIN, BOOKING_DATE);

        cancelCurrentBooking(ADMIN);

        assertTrue(bookings.findByUser(CLIENT).isEmpty());
        Message clientNotice = bot.messagesTo(CLIENT).stream()
                .filter(message -> message.text() != null
                        && message.text().startsWith("Администратор отменил вашу запись:"))
                .findFirst().orElseThrow();
        assertTrue(clientNotice.text().contains("База «Центр», Зал 1"));
        assertTrue(clientNotice.text().contains("18:00-20:00"));
        assertEquals(List.of("Горячие слоты", "Забронировать"),
                clientNotice.keyboard().stream().flatMap(List::stream).map(Button::text).toList());

        Message ownerNotice = bot.messagesTo(OWNER).stream()
                .filter(message -> message.text() != null && message.text().contains("@anna_admin"))
                .findFirst().orElseThrow();
        assertTrue(ownerNotice.text().startsWith("Администратор @anna_admin отменил запись:"));
        assertTrue(ownerNotice.text().contains("Клиент: Анна, +79991234567."));
        assertTrue(ownerNotice.text().contains("Комментарий: Нужен микрофон."));
        assertTrue(bot.messages().stream().noneMatch(message -> message.text() != null
                && message.text().contains("неустойк")));

        assertTrue(bot.messagesTo(ADMIN).stream()
                .anyMatch(message -> "Запись отменена.".equals(message.text())));
        assertEquals("На этот день записей нет.", lastText());
    }

    /// Владелец отменяет запись и не получает уведомление о собственной отмене.
    @Test
    void ownerCancelsBookingWithoutNotifyingHimself() {
        addBooking("");
        openDay(OWNER, BOOKING_DATE);

        cancelCurrentBooking(OWNER);

        assertTrue(bookings.findByUser(CLIENT).isEmpty());
        assertTrue(bot.messagesTo(CLIENT).stream().anyMatch(message -> message.text() != null
                && message.text().startsWith("Владелец отменил вашу запись:")));
        assertTrue(bot.messagesTo(OWNER).stream().noneMatch(message -> message.text() != null
                && message.text().contains("отменил запись")));
    }

    /// Если освободившееся время «горячее», подписчики получают рассылку.
    @Test
    void hotSlotCancellationNotifiesSubscribers() {
        subscriptions.subscribe(SUBSCRIBER, base.id());
        addBooking("");
        openDay(ADMIN, BOOKING_DATE);

        cancelCurrentBooking(ADMIN);

        assertTrue(bot.messagesTo(SUBSCRIBER).stream().anyMatch(message -> message.text() != null
                && message.text().startsWith("«Горячий» слот: база «Центр»")));
    }

    /// Прошедшая запись не показывает кнопку отмены, а прямой callback отклоняется.
    @Test
    void pastBookingCannotBeCancelled() {
        LocalDateTime past = LocalDateTime.of(2026, 10, 19, 10, 0);
        addBookingAt(past, "");
        openDay(ADMIN, past.toLocalDate());

        assertTrue(lastText().contains("10:00-12:00"));
        assertFalse(lastButtonsData().stream().anyMatch(data -> data.startsWith("schedule:cancel:")));

        String startKey = past.format(callbackFormat);
        press(ADMIN, "schedule:cancel:" + base.id() + ":" + room.id() + ":" + startKey);
        assertEquals("Запись уже началась, отменить её нельзя.", lastText());
        press(ADMIN, "schedule:cancelyes:" + base.id() + ":" + room.id() + ":" + startKey);
        assertEquals("Запись уже началась, отменить её нельзя.", lastText());
        assertFalse(bookings.findByUser(CLIENT).isEmpty());
    }

    /// Открывает расписание указанного дня.
    ///
    /// @param user администратор или владелец
    /// @param date день расписания
    private void openDay(long user, LocalDate date) {
        press(user, "schedule:day:" + base.id() + ":" + date);
    }

    /// Отменяет первую доступную запись на открытом дне.
    ///
    /// @param user администратор или владелец
    private void cancelCurrentBooking(long user) {
        String cancel = lastButtonsData().stream()
                .filter(data -> data.startsWith("schedule:cancel:"))
                .findFirst().orElseThrow();
        press(user, cancel);
        String confirm = lastButtonsData().stream()
                .filter(data -> data.startsWith("schedule:cancelyes:"))
                .findFirst().orElseThrow();
        press(user, confirm);
    }

    /// Добавляет запись клиента на тестовый день.
    ///
    /// @param comment комментарий клиента
    private void addBooking(String comment) {
        addBookingAt(LocalDateTime.of(2026, 10, 21, 18, 0), comment);
    }

    /// Добавляет запись клиента на указанное время.
    ///
    /// @param start   время начала
    /// @param comment комментарий клиента
    private void addBookingAt(LocalDateTime start, String comment) {
        bookings.addIfFree(new Booking(room.id(), CLIENT, start, 2, "Анна", "+79991234567", comment));
    }

    /// Отправляет сервису нажатие кнопки.
    ///
    /// @param user клиент или сотрудник
    /// @param data данные кнопки
    private void press(long user, String data) {
        service.processMessage(new Message(null, data, List.of()), user, bot);
    }

    /// Отправляет сервису текст.
    ///
    /// @param user клиент или сотрудник
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
