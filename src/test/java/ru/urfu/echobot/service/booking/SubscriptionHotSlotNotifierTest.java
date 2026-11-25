package ru.urfu.echobot.service.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.bot.DummyBot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.SqliteBaseRepository;
import ru.urfu.echobot.repository.SqliteDatabase;
import ru.urfu.echobot.repository.SqliteSubscriptionRepository;
import ru.urfu.echobot.service.Button;

/// Тесты рассылки уведомлений о «горячих» слотах.
final class SubscriptionHotSlotNotifierTest {
    /// Подписчик базы «Центр».
    private static final long SUBSCRIBER = 101L;
    /// Подписчик другой базы.
    private static final long OTHER_SUBSCRIBER = 102L;
    /// Владелец базы, который не подписан.
    private static final long OWNER = 103L;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
    private final Base base = new Base(1, OWNER, "Центр", "Ленина, 1", "Europe/Moscow", 2, "",
            Map.of(), 48, 100);
    private final Room room = new Room(5, 1, "Зал 1", false);
    private final Booking booking = new Booking(5, 42, LocalDateTime.of(2026, 10, 21, 20, 0), 2,
            "Анна", "+79990000000", "");
    /// Временная директория для файла базы данных.
    @TempDir
    private Path temporaryDirectory;
    /// Хранилище подписок в SQLite.
    private SqliteSubscriptionRepository subscriptions;
    /// Рассылка уведомлений подписчикам.
    private SubscriptionHotSlotNotifier notifier;

    /// Создаёт хранилище подписок и рассылку.
    @BeforeEach
    void setUp() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
        SqliteBaseRepository bases = new SqliteBaseRepository(database);
        bases.save(base);
        bases.save(new Base(0, OWNER, "Север", "Ленина, 2", "Europe/Moscow", 2, "",
                Map.of(), 48, 100));
        subscriptions = new SqliteSubscriptionRepository(database);
        notifier = new SubscriptionHotSlotNotifier(subscriptions, new BookingRules(clock));
    }

    /// Given: подписан только клиент базы. When: освобождается слот. Then: уведомление получает он один.
    @Test
    void notificationIsSentOnlyToSubscribersOfTheBase() {
        subscriptions.subscribe(SUBSCRIBER, base.id());
        subscriptions.subscribe(OTHER_SUBSCRIBER, 2);
        DummyBot bot = new DummyBot();

        notifier.notifySubscribers(base, room, booking, bot);

        assertEquals(1, bot.messagesTo(SUBSCRIBER).size());
        assertTrue(bot.messagesTo(OTHER_SUBSCRIBER).isEmpty());
        assertTrue(bot.messagesTo(OWNER).isEmpty());
        assertTrue(bot.messagesTo(SUBSCRIBER).getFirst().text().contains(
                "«Горячий» слот: база «Центр», Зал 1, завтра, 20:00-22:00."));
    }

    /// Given: подписчик базы. When: приходит уведомление. Then: есть кнопки бронирования и пропуска.
    @Test
    void notificationHasBookingAndSkipButtons() {
        subscriptions.subscribe(SUBSCRIBER, base.id());
        DummyBot bot = new DummyBot();

        notifier.notifySubscribers(base, room, booking, bot);

        List<Button> buttons = bot.messagesTo(SUBSCRIBER).getFirst().keyboard().stream()
                .flatMap(List::stream).toList();
        assertEquals(List.of("Забронировать", "Пропустить"), buttons.stream().map(Button::text).toList());
        assertEquals("hot:book:5:202610212000", buttons.getFirst().data());
        assertEquals("hot:skip", buttons.get(1).data());
    }
}
