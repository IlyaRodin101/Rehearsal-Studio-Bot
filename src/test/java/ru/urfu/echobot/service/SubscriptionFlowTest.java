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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

/// Сквозные тесты подписок и уведомлений при отмене брони.
final class SubscriptionFlowTest {
    /// Клиент, создающий запись.
    private static final long CLIENT = 42L;
    /// Клиент-подписчик.
    private static final long SUBSCRIBER = 43L;
    /// Владелец базы.
    private static final long OWNER = 50L;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
    @TempDir
    private Path temporaryDirectory;
    private SqliteBookingRepository bookings;
    private SqliteSubscriptionRepository subscriptions;
    private BookingMessageProcessingService service;
    private DummyBot bot;
    private Base base;
    private Room room;

    /// Создаёт базу, зал и сервис обработки сообщений.
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
        service = new BookingMessageProcessingService(bases, rooms, bookings, new BookingRules(clock),
                Set.of(OWNER), subscriptions);
        bot = new DummyBot();
    }

    /// Given: клиент без подписки. When: включает и выключает подписку. Then: отметка меняется, БД обновляется.
    @Test
    void clientTogglesSubscriptionWithButtons() {
        press(CLIENT, "menu:sub");
        press(CLIENT, "sub:toggle:" + base.id());

        assertTrue(bot.messages().stream().anyMatch(message ->
                "Подписка на «Центр» включена.".equals(message.text())));
        assertTrue(subscriptions.isSubscribed(CLIENT, base.id()));
        assertTrue(lastButtons().contains("✓ Центр"));

        press(CLIENT, "sub:toggle:" + base.id());

        assertTrue(bot.messages().stream().anyMatch(message ->
                "Подписка на «Центр» отключена.".equals(message.text())));
        assertFalse(subscriptions.isSubscribed(CLIENT, base.id()));
        assertFalse(lastButtons().contains("✓ Центр"));
    }

    /// Given: подписан только один клиент базы. When: другой клиент отменяет бронь.
    /// Then: уведомление получает только подписчик.
    @Test
    void cancellationNotifiesOnlySubscriberOfTheBase() {
        subscriptions.subscribe(SUBSCRIBER, base.id());
        bookings.addIfFree(new Booking(room.id(), CLIENT, LocalDateTime.of(2026, 10, 21, 18, 0), 2,
                "Анна", "+79990000000", ""));

        press(CLIENT, "menu:mine");
        String cancel = lastButtonsData().stream().filter(data -> data.startsWith("mine:cancel:"))
                .findFirst().orElseThrow();
        press(CLIENT, cancel);
        String confirm = lastButtonsData().stream().filter(data -> data.startsWith("mine:cancelyes:"))
                .findFirst().orElseThrow();
        press(CLIENT, confirm);

        assertTrue(bookings.findByUser(CLIENT).isEmpty());
        assertTrue(bot.messagesTo(SUBSCRIBER).stream().anyMatch(message ->
                message.text() != null && message.text().startsWith("«Горячий» слот: база «Центр»")));
        assertTrue(bot.messagesTo(CLIENT).stream().noneMatch(message ->
                message.text() != null && message.text().startsWith("«Горячий» слот:")));
    }

    /// Given: два клиента одновременно подписываются. When: Освобождается слот. Then: уведомления получают оба.
    @Test
    void simultaneousClientsSubscribeAndReceiveNotifications() throws Exception {
        DummyBot firstBot = new DummyBot();
        DummyBot secondBot = new DummyBot();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> subscribeQuietly(CLIENT, firstBot, start));
            executor.submit(() -> subscribeQuietly(SUBSCRIBER, secondBot, start));
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertTrue(subscriptions.isSubscribed(CLIENT, base.id()));
        assertTrue(subscriptions.isSubscribed(SUBSCRIBER, base.id()));
        SubscriptionHotSlotNotifier notifier = new SubscriptionHotSlotNotifier(subscriptions,
                new BookingRules(clock));
        DummyBot delivery = new DummyBot();
        notifier.notifySubscribers(base, room, new Booking(room.id(), CLIENT,
                LocalDateTime.of(2026, 10, 21, 18, 0), 2, "Анна", "+79990000000", ""), delivery);

        assertEquals(1, delivery.messagesTo(CLIENT).size());
        assertEquals(1, delivery.messagesTo(SUBSCRIBER).size());
    }

    /// Подписывается одновременно с другим клиентом.
    ///
    /// @param user  клиент
    /// @param bot   бот клиента
    /// @param start сигнал одновременного старта
    /// @throws InterruptedException если ожидание прервано
    private void subscribeConcurrently(long user, DummyBot bot, CountDownLatch start)
            throws InterruptedException {
        start.await();
        service.processMessage(new Message(null, "sub:toggle:" + base.id(), List.of()), user, bot);
    }

    /// Подписывается в фоновом потоке, восстанавливая флаг прерывания.
    ///
    /// @param user  клиент
    /// @param bot   бот клиента
    /// @param start сигнал одновременного старта
    private void subscribeQuietly(long user, DummyBot bot, CountDownLatch start) {
        try {
            subscribeConcurrently(user, bot, start);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /// Отправляет сервису нажатие кнопки.
    ///
    /// @param user клиент
    /// @param data данные кнопки
    private void press(long user, String data) {
        service.processMessage(new Message(null, data, List.of()), user, bot);
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
}
