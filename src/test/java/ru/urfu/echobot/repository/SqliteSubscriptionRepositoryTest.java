package ru.urfu.echobot.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;

/// Тесты хранения подписок в SQLite.
final class SqliteSubscriptionRepositoryTest {
    /// Идентификатор клиента.
    private static final long USER = 101L;
    /// Идентификатор второго клиента.
    private static final long OTHER_USER = 102L;

    @TempDir
    private Path temporaryDirectory;
    private SqliteDatabase database;
    private Base base;
    private Base otherBase;
    private SqliteSubscriptionRepository subscriptions;

    /// Создаёт базы и хранилище подписок.
    @BeforeEach
    void setUp() {
        database = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
        SqliteBaseRepository bases = new SqliteBaseRepository(database);
        base = bases.save(base("Центр"));
        otherBase = bases.save(base("Север"));
        subscriptions = new SqliteSubscriptionRepository(database);
    }

    /// Given: повторная подписка. When: сохраняем дважды. Then: дубль не создаётся.
    @Test
    void repeatedSubscriptionCreatesSingleRow() {
        subscriptions.subscribe(USER, base.id());
        subscriptions.subscribe(USER, base.id());

        assertTrue(subscriptions.isSubscribed(USER, base.id()));
        assertEquals(List.of(base.id()), subscriptions.findBaseIdsByUser(USER));
        assertEquals(List.of(USER), subscriptions.findUserIdsByBase(base.id()));
    }

    /// Given: клиент подписан. When: отписывается. Then: подписка удалена.
    @Test
    void unsubscribeRemovesSubscription() {
        subscriptions.subscribe(USER, base.id());
        subscriptions.unsubscribe(USER, base.id());

        assertFalse(subscriptions.isSubscribed(USER, base.id()));
        assertTrue(subscriptions.findBaseIdsByUser(USER).isEmpty());
        assertTrue(subscriptions.findUserIdsByBase(base.id()).isEmpty());
    }

    /// Given: подписки сохранены. When: хранилище создаётся заново. Then: подписки сохранились.
    @Test
    void subscriptionsSurviveRepositoryRecreation() {
        subscriptions.subscribe(USER, base.id());
        subscriptions.subscribe(OTHER_USER, otherBase.id());

        SqliteDatabase reopened = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
        SqliteSubscriptionRepository restored = new SqliteSubscriptionRepository(reopened);

        assertTrue(restored.isSubscribed(USER, base.id()));
        assertTrue(restored.isSubscribed(OTHER_USER, otherBase.id()));
        assertFalse(restored.isSubscribed(USER, otherBase.id()));
    }

    /// Создаёт базу с пустым графиком.
    ///
    /// @param name название базы
    /// @return новая база
    private Base base(String name) {
        return new Base(0, 0, name, "Ленина, 1", "Europe/Moscow", 2, "", Map.<DayOfWeek, OpeningHours>of());
    }
}
