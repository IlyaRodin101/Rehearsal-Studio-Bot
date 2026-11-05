package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.TestStudioData;
import ru.urfu.echobot.bot.DummyBot;

/// Тесты диалогов бронирования без Telegram. Время зафиксировано: вторник, 20 октября 2026, 10:00 по Москве.
/// В начальных данных заняты: «Центр», Зал 1, 21 октября 18:00-20:00 и «Север», Студия, 22 октября 13:00-16:00.
final class BookingMessageProcessingServiceTest {
    /// Chat ID used to verify that group replies do not become conversation identifiers.
    private final long groupChat = -1000001L;
    /// Первый клиент.
    private final long client = 42L;
    /// Второй клиент.
    private final long otherClient = 43L;
    /// Temporary directory that isolates the SQLite database for each test.
    @TempDir
    private Path temporaryDirectory;

    /// Бот-заглушка, собирающая ответы.
    private DummyBot bot;
    /// Проверяемый сервис.
    private BookingMessageProcessingService service;
    /// SQLite repositories and records used by the current test.
    private TestStudioData data;

    /// Создаёт сервис с начальными данными и фиксированным временем перед каждым тестом.
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
        data = new TestStudioData(temporaryDirectory, clock);
        service = new BookingMessageProcessingService(data.baseRepository(), data.roomRepository(),
                data.bookingRepository(), new BookingRules(clock));
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
        type(client, "/start");

        assertEquals(List.of("Забронировать", "Мои записи", "О базе"), lastButtons());
    }

    /// Owners retain the client menu and receive the additional base-management button.
    @Test
    void ownerSeesBaseManagementAndClientActions() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
        BookingMessageProcessingService ownerService = new BookingMessageProcessingService(
                data.baseRepository(), data.roomRepository(), data.bookingRepository(),
                new BookingRules(clock), Set.of(client));

        ownerService.processMessage(new Message("/start", null, List.of()), groupChat, client, bot);

        assertTrue(lastButtons().containsAll(List.of("Забронировать", "Мои записи", "О базе", "Базы")));
        ownerService.processMessage(new Message(null, "menu:bases", List.of()), groupChat, client, bot);

        assertEquals(groupChat, bot.lastChatId());
        assertEquals("У вас пока нет баз:", lastText());
    }

    /// Verifies that a group message is replied to in its chat while booking state uses the sender ID.
    @Test
    void groupBookingUsesSenderIdForStateAndChatIdForReplies() {
        processInGroup(new Message(null, "menu:book", List.of()));
        processInGroup(new Message(null, "book:base:1", List.of()));
        processInGroup(new Message(null, "book:room:1", List.of()));
        processInGroup(new Message(null, "book:day:2026-10-24", List.of()));
        processInGroup(new Message(null, "book:time:2026-10-24:18", List.of()));
        processInGroup(new Message(null, "book:hours:2", List.of()));
        processInGroup(new Message("Групповой клиент", null, List.of()));
        processInGroup(new Message("+7 999 123-45-67", null, List.of()));
        processInGroup(new Message(null, "book:skip", List.of()));
        processInGroup(new Message(null, "book:confirm", List.of()));

        assertEquals(groupChat, bot.lastChatId());
        assertEquals(1, data.bookingRepository().findByUser(client).size());
        assertTrue(data.bookingRepository().findByUser(groupChat).isEmpty());
    }

    /// Sends one test update to the group using distinct chat and sender IDs.
    ///
    /// @param message Telegram message to process
    private void processInGroup(Message message) {
        service.processMessage(message, groupChat, client, bot);
    }

    /// Given: клиент проходит весь диалог. When: подтверждает бронь. Then: запись создана и видна в «Мои записи».
    @Test
    void clientBooksRoomAndSeesItInMyBookings() {
        chooseSlot(client, 1, 1, "2026-10-24", 18);
        type(client, "Анна");
        type(client, "+7 999 123-45-67");
        press(client, "book:skip");

        assertTrue(lastText().contains("База «Центр», Зал 1"));
        assertTrue(lastText().contains("18:00-20:00"));
        assertTrue(lastText().contains("Телефон: +79991234567"));

        press(client, "book:confirm");
        assertTrue(bot.messages().stream().anyMatch(m -> "Готово, запись создана.".equals(m.text())));

        press(client, "menu:mine");
        assertTrue(bot.messages().stream().anyMatch(m -> m.text() != null
                && m.text().contains("Центр, Зал 1") && m.text().contains("18:00-20:00")));
    }

    /// Given: у клиента нет записей. When: открывает «Мои записи». Then: бот сообщает, что записей нет.
    @Test
    void myBookingsIsEmptyForNewClient() {
        press(client, "menu:mine");

        assertTrue(bot.messages().stream().anyMatch(m -> "У вас пока нет будущих записей.".equals(m.text())));
    }

    /// Given: клиент вводит неверный телефон. When: отправляет «12345». Then: бот просит ввести номер снова.
    @Test
    void invalidPhoneIsRejected() {
        chooseSlot(client, 1, 1, "2026-10-24", 18);
        type(client, "Анна");
        type(client, "12345");

        assertTrue(lastText().startsWith("Не удалось распознать номер"));
    }

    /// Given: зал «Студия» требует комментарий. When: клиент вводит «-». Then: бот требует описать занятие,
    /// а настоящий комментарий принимается.
    @Test
    void requiredCommentCannotBeSkipped() {
        chooseSlot(client, 2, 4, "2026-10-24", 18);
        type(client, "Игорь");
        type(client, "8 912 000-11-22");
        assertFalse(lastButtons().contains("Пропустить"));

        type(client, "-");
        assertTrue(lastText().startsWith("Комментарий не может быть пустым"));

        type(client, "Запись барабанов и баса");
        assertTrue(lastText().contains("Комментарий: Запись барабанов и баса"));
    }

    /// Given: первый клиент занял слот, второй уже дошёл до подтверждения. When: второй подтверждает.
    /// Then: он получает сообщение, что время недоступно.
    @Test
    void secondClientGetsMessageWhenSlotIsTaken() {
        chooseSlot(client, 1, 1, "2026-10-24", 18);
        type(client, "Анна");
        type(client, "+7 999 123-45-67");
        press(client, "book:skip");
        chooseSlot(otherClient, 1, 1, "2026-10-24", 18);
        type(otherClient, "Игорь");
        type(otherClient, "+7 912 000-11-22");
        press(otherClient, "book:skip");

        press(client, "book:confirm");
        press(otherClient, "book:confirm");

        assertTrue(lastText().startsWith("Это время больше недоступно"));
    }

    /// Given: на 21 октября Зал 1 «Центра» занят с 18:00 до 20:00. When: клиент смотрит время.
    /// Then: 18:00 и 19:00 не предлагаются.
    @Test
    void occupiedHoursAreNotOffered() {
        press(client, "menu:book");
        press(client, "book:base:1");
        press(client, "book:room:1");
        press(client, "book:day:2026-10-21");

        assertTrue(lastButtons().contains("17:00"));
        assertFalse(lastButtons().contains("18:00"));
        assertFalse(lastButtons().contains("19:00"));
        assertTrue(lastButtons().contains("20:00"));
    }

    /// Given: сейчас 10:00, база просит бронировать не позднее чем за 2 часа. When: клиент смотрит время на сегодня.
    /// Then: раньше 12:00 времени нет.
    @Test
    void timesCloserThanLeadTimeAreHidden() {
        press(client, "menu:book");
        press(client, "book:base:1");
        press(client, "book:room:1");
        press(client, "book:day:2026-10-20");

        assertFalse(lastButtons().contains("9:00"));
        assertFalse(lastButtons().contains("11:00"));
        assertTrue(lastButtons().contains("12:00"));
    }

    /// Given: в воскресенье база закрыта. When: клиент выбирает воскресенье. Then: свободного времени нет.
    @Test
    void closedDayHasNoTimes() {
        press(client, "menu:book");
        press(client, "book:base:1");
        press(client, "book:room:1");
        press(client, "book:day:2026-10-25");

        assertEquals("На этот день свободного времени нет.", lastText());
    }

    /// Given: клиент открывает информацию о базе. When: выбирает «Север». Then: видит адрес, график и оборудование.
    @Test
    void aboutBaseShowsAddressScheduleAndEquipment() {
        press(client, "menu:about");
        press(client, "about:base:2");

        assertTrue(lastText().contains("ул. Ленина, 15"));
        assertTrue(lastText().contains("Вс: выходной"));
        assertTrue(lastText().contains("микшерный пульт"));
    }

    /// Given: клиент на первом шаге бронирования. When: нажимает «Назад». Then: попадает в главное меню.
    @Test
    void backFromFirstStepReturnsToMainMenu() {
        press(client, "menu:book");
        press(client, "back");

        assertEquals(List.of("Забронировать", "Мои записи", "О базе"), lastButtons());
    }

    /// Проверяет, что при старте в 23:00 длительность ограничена одним часом до закрытия в 24:00.
    @Test
    void durationAfterTwentyThreeIsLimitedToOneHour() {
        press(client, "menu:book");
        press(client, "book:base:1");
        press(client, "book:room:2");
        press(client, "book:day:2026-10-24");
        press(client, "book:time:2026-10-24:23");

        assertEquals(List.of("1", "Назад"), lastButtons());
    }

    /// Проверяет, что возврат из ввода телефона сбрасывает ранее введённые контактные данные.
    @Test
    void goingBackInCustomerFormClearsPreviouslyEnteredValues() {
        chooseSlot(client, 1, 2, "2026-10-24", 18);
        type(client, "Старое имя");
        type(client, "+7 999 111-22-33");
        press(client, "back");
        type(client, "Новое имя");
        type(client, "+7 999 444-55-66");
        press(client, "book:skip");

        assertTrue(lastText().contains("Имя: Новое имя"));
        assertTrue(lastText().contains("Телефон: +79994445566"));
        assertFalse(lastText().contains("Старое имя"));
        assertFalse(lastText().contains("+79991112233"));
    }
}
