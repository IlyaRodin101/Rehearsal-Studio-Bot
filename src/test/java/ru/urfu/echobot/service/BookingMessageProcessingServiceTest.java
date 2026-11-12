package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

/// Tests booking conversations against isolated SQLite data without Telegram.
final class BookingMessageProcessingServiceTest {
    /// Первый клиент.
    private static final long CLIENT = 42L;
    /// Второй клиент.
    private static final long OTHER_CLIENT = 43L;
    /// Владелец тестовых баз.
    private static final long OWNER = 101L;
    private static final long SERVICE_USER_ID = 0L;

    /// Бот-заглушка, собирающая ответы.
    private DummyBot bot;
    /// Проверяемый сервис.
    private BookingMessageProcessingService service;
    @TempDir
    private Path temporaryDirectory;
    private SqliteBaseRepository baseRepository;
    private SqliteBookingRepository bookingRepository;
    private SqliteRoomRepository roomRepository;

    /// Creates isolated SQLite repositories and fixture bases before each test.
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
        baseRepository = new SqliteBaseRepository(database);
        roomRepository = new SqliteRoomRepository(database);
        bookingRepository = new SqliteBookingRepository(database);
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day != DayOfWeek.SUNDAY) {
                schedule.put(day, new OpeningHours(9, 24));
            }
        }
        Base center = baseRepository.save(new Base(0, OWNER, "Центр", "ул. Ленина, 1", "Europe/Moscow",
                2, "Барабаны: акустическая установка", schedule));
        Base north = baseRepository.save(new Base(0, OWNER, "Север", "ул. Ленина, 15", "Europe/Moscow",
                2, "Барабаны, 2 комбоусилителя, 4 микрофона, микшерный пульт", schedule));
        roomRepository.save(new Room(0, center.id(), "Зал 1", false));
        roomRepository.save(new Room(0, center.id(), "Зал 2", false));
        roomRepository.save(new Room(0, north.id(), "Зал 1", false));
        roomRepository.save(new Room(0, north.id(), "Студия", true));
        service = new BookingMessageProcessingService(baseRepository, roomRepository, bookingRepository,
                new BookingRules(clock));
        bot = new DummyBot();
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

    /// Нажимает кнопки до шага «имя» для бронирования на 2 часа в указанный день и час.
    ///
    /// @param user   клиент
    /// @param baseId база
    /// @param roomId зал
    /// @param date   дата ГГГГ-ММ-ДД
    /// @param hour   час начала
    private void chooseSlot(long user, int baseId, int roomId, String date, int hour) {
        press(user, "menu:book");
        press(user, "book:base:" + baseId);
        press(user, "book:room:" + roomId);
        press(user, "book:day:" + date);
        press(user, "book:time:" + date + ":" + hour);
        press(user, "book:hours:2");
    }

    /// Возвращает подписи кнопок последнего сообщения бота.
    ///
    /// @return подписи кнопок по порядку
    private List<String> lastButtons() {
        return bot.lastMessage().keyboard().stream().flatMap(List::stream).map(Button::text).toList();
    }

    /// Возвращает текст последнего сообщения бота.
    ///
    /// @return текст сообщения
    private String lastText() {
        return bot.lastMessage().text();
    }

    /// Given: любой клиент. When: отправляет /start. Then: в меню три кнопки.
    @Test
    void startShowsMainMenuButtons() {
        type(CLIENT, "/start");

        assertEquals(List.of("Забронировать", "Мои записи", "О базе"), lastButtons());
    }

    /// Owners retain the client menu and receive the additional base-management button.
    @Test
    void ownerSeesBaseManagementAndClientActions() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
        BookingMessageProcessingService ownerService = new BookingMessageProcessingService(
                baseRepository,
                roomRepository,
                bookingRepository,
                new BookingRules(clock), Set.of(CLIENT));

        ownerService.processMessage(new Message("/start", null, List.of()), CLIENT, bot);

        assertTrue(lastButtons().containsAll(List.of("Забронировать", "Мои записи", "О базе", "Базы")));
    }

    /// Given: клиент проходит весь диалог. When: подтверждает бронь. Then: запись создана и видна в «Мои записи».
    @Test
    void clientBooksRoomAndSeesItInMyBookings() {
        chooseSlot(CLIENT, 1, 1, "2026-10-24", 18);
        type(CLIENT, "Анна");
        type(CLIENT, "+7 999 123-45-67");
        press(CLIENT, "book:skip");

        assertTrue(lastText().contains("База «Центр», Зал 1"));
        assertTrue(lastText().contains("18:00-20:00"));
        assertTrue(lastText().contains("Телефон: +79991234567"));

        press(CLIENT, "book:confirm");
        assertTrue(bot.messages().stream().anyMatch(m -> "Готово, запись создана.".equals(m.text())));

        press(CLIENT, "menu:mine");
        assertTrue(bot.messages().stream().anyMatch(m -> m.text() != null
                && m.text().contains("Центр, Зал 1") && m.text().contains("18:00-20:00")));
    }

    /// Клиент отменяет свою будущую запись после подтверждения; другой клиент не может её отменить.
    @Test
    void clientCanCancelOwnBookingButNotAnotherClientsBooking() {
        chooseSlot(CLIENT, 1, 1, "2026-10-24", 18);
        type(CLIENT, "Анна");
        type(CLIENT, "+7 999 123-45-67");
        press(CLIENT, "book:skip");
        press(CLIENT, "book:confirm");
        press(CLIENT, "menu:mine");

        assertTrue(lastButtons().contains("Отменить • Центр • Зал 1"));
        String confirmationCallback = bot.lastMessage().keyboard().stream()
                .flatMap(List::stream)
                .map(Button::data)
                .filter(data -> data.startsWith("mine:cancel:"))
                .findFirst()
                .orElseThrow();
        press(CLIENT, confirmationCallback);
        assertTrue(lastButtons().contains("Да, отменить"));
        assertTrue(lastText().contains("До начала больше 48 часов, отмена без неустойки."));
        String cancelCallback = bot.lastMessage().keyboard().stream()
                .flatMap(List::stream)
                .map(Button::data)
                .filter(data -> data.startsWith("mine:cancelyes:"))
                .findFirst()
                .orElseThrow();

        press(OTHER_CLIENT, cancelCallback);
        assertTrue(lastText().contains("Запись не найдена"));
        press(CLIENT, cancelCallback);

        assertTrue(bot.messages().stream().anyMatch(m -> "Запись отменена.".equals(m.text())));
        assertTrue(bot.messages().stream().anyMatch(m -> m.text() != null
                && m.text().startsWith("Клиент отменил запись бесплатно:")));
        assertEquals("У вас пока нет будущих записей.", lastText());
    }

    /// Given: у клиента нет записей. When: открывает «Мои записи». Then: бот сообщает, что записей нет.
    @Test
    void myBookingsIsEmptyForNewClient() {
        press(CLIENT, "menu:mine");

        assertTrue(bot.messages().stream().anyMatch(m -> "У вас пока нет будущих записей.".equals(m.text())));
    }

    /// Given: клиент вводит неверный телефон. When: отправляет «12345». Then: бот просит ввести номер снова.
    @Test
    void invalidPhoneIsRejected() {
        chooseSlot(CLIENT, 1, 1, "2026-10-24", 18);
        type(CLIENT, "Анна");
        type(CLIENT, "12345");

        assertTrue(lastText().startsWith("Не удалось распознать номер"));
    }

    /// Given: зал «Студия» требует комментарий. When: клиент вводит «-». Then: бот требует описать занятие,
    /// а настоящий комментарий принимается.
    @Test
    void requiredCommentCannotBeSkipped() {
        chooseSlot(CLIENT, 2, 4, "2026-10-24", 18);
        type(CLIENT, "Игорь");
        type(CLIENT, "8 912 000-11-22");
        assertFalse(lastButtons().contains("Пропустить"));

        type(CLIENT, "-");
        assertTrue(lastText().startsWith("Комментарий не может быть пустым"));

        type(CLIENT, "Запись барабанов и баса");
        assertTrue(lastText().contains("Комментарий: Запись барабанов и баса"));
    }

    /// Given: первый клиент занял слот, второй уже дошёл до подтверждения. When: второй подтверждает.
    /// Then: он получает сообщение, что время недоступно.
    @Test
    void secondClientGetsMessageWhenSlotIsTaken() {
        chooseSlot(CLIENT, 1, 1, "2026-10-24", 18);
        type(CLIENT, "Анна");
        type(CLIENT, "+7 999 123-45-67");
        press(CLIENT, "book:skip");
        chooseSlot(OTHER_CLIENT, 1, 1, "2026-10-24", 18);
        type(OTHER_CLIENT, "Игорь");
        type(OTHER_CLIENT, "+7 912 000-11-22");
        press(OTHER_CLIENT, "book:skip");

        press(CLIENT, "book:confirm");
        press(OTHER_CLIENT, "book:confirm");

        assertTrue(lastText().startsWith("Это время больше недоступно"));
    }

    /// Given: на 21 октября Зал 1 «Центра» занят с 18:00 до 20:00. When: клиент смотрит время.
    /// Then: 18:00 и 19:00 не предлагаются.
    @Test
    void occupiedHoursAreNotOffered() {
        bookingRepository.addIfFree(new Booking(1, SERVICE_USER_ID, LocalDateTime.of(2026, 10, 21, 18, 0),
                2, "Служебная запись", "+70000000000", ""));
        press(CLIENT, "menu:book");
        press(CLIENT, "book:base:1");
        press(CLIENT, "book:room:1");
        press(CLIENT, "book:day:2026-10-21");

        assertTrue(lastButtons().contains("17:00"));
        assertFalse(lastButtons().contains("18:00"));
        assertFalse(lastButtons().contains("19:00"));
        assertTrue(lastButtons().contains("20:00"));
    }

    /// Given: сейчас 10:00, база просит бронировать не позднее чем за 2 часа. When: клиент смотрит время на сегодня.
    /// Then: раньше 12:00 времени нет.
    @Test
    void timesCloserThanLeadTimeAreHidden() {
        press(CLIENT, "menu:book");
        press(CLIENT, "book:base:1");
        press(CLIENT, "book:room:1");
        press(CLIENT, "book:day:2026-10-20");

        assertFalse(lastButtons().contains("9:00"));
        assertFalse(lastButtons().contains("11:00"));
        assertTrue(lastButtons().contains("12:00"));
    }

    /// Given: в воскресенье база закрыта. When: клиент выбирает воскресенье. Then: свободного времени нет.
    @Test
    void closedDayHasNoTimes() {
        press(CLIENT, "menu:book");
        press(CLIENT, "book:base:1");
        press(CLIENT, "book:room:1");
        press(CLIENT, "book:day:2026-10-25");

        assertEquals("На этот день свободного времени нет.", lastText());
    }

    /// Given: клиент открывает информацию о базе. When: выбирает «Север». Then: видит адрес, график и оборудование.
    @Test
    void aboutBaseShowsAddressScheduleAndEquipment() {
        press(CLIENT, "menu:about");
        press(CLIENT, "about:base:2");

        assertTrue(lastText().contains("ул. Ленина, 15"));
        assertTrue(lastText().contains("Вс: выходной"));
        assertTrue(lastText().contains("микшерный пульт"));
    }

    /// Given: клиент на первом шаге бронирования. When: нажимает «Назад». Then: попадает в главное меню.
    @Test
    void backFromFirstStepReturnsToMainMenu() {
        press(CLIENT, "menu:book");
        press(CLIENT, "back");

        assertEquals(List.of("Забронировать", "Мои записи", "О базе"), lastButtons());
    }

    /// Проверяет, что при старте в 23:00 длительность ограничена одним часом до закрытия в 24:00.
    @Test
    void durationAfterTwentyThreeIsLimitedToOneHour() {
        press(CLIENT, "menu:book");
        press(CLIENT, "book:base:1");
        press(CLIENT, "book:room:2");
        press(CLIENT, "book:day:2026-10-24");
        press(CLIENT, "book:time:2026-10-24:23");

        assertEquals(List.of("1", "Назад"), lastButtons());
    }

    /// Проверяет, что возврат из ввода телефона сбрасывает ранее введённые контактные данные.
    @Test
    void goingBackInCustomerFormClearsPreviouslyEnteredValues() {
        chooseSlot(CLIENT, 1, 2, "2026-10-24", 18);
        type(CLIENT, "Старое имя");
        type(CLIENT, "+7 999 111-22-33");
        press(CLIENT, "back");
        type(CLIENT, "Новое имя");
        type(CLIENT, "+7 999 444-55-66");
        press(CLIENT, "book:skip");

        assertTrue(lastText().contains("Имя: Новое имя"));
        assertTrue(lastText().contains("Телефон: +79994445566"));
        assertFalse(lastText().contains("Старое имя"));
        assertFalse(lastText().contains("+79991112233"));
    }
}
