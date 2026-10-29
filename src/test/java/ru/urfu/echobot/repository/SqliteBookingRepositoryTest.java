package ru.urfu.echobot.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.model.Booking;

/// Проверяет сохранность броней и проверку пересечений в SQLite.
final class SqliteBookingRepositoryTest {
    /// Временный каталог для изолированных файлов тестовой базы данных.
    @TempDir
    private Path temporaryDirectory;

    /// Проверяет, что добавленная бронь доступна после закрытия и повторного открытия репозитория.
    @Test
    void bookingsSurviveRepositoryRestart() throws Exception {
        Path databasePath = temporaryDirectory.resolve("persist.db");
        Booking booking = booking(10L, 101L, "2030-04-12T18:00", 2);

        try (SqliteBookingRepository repository = new SqliteBookingRepository(databasePath)) {
            repository.refreshInitialBookings(List.of());
            assertTrue(repository.addIfFree(booking));
        }

        try (SqliteBookingRepository repository = new SqliteBookingRepository(databasePath)) {
            assertEquals(List.of(booking), repository.findByUser(101L));
        }
    }

    /// Проверяет, что бронь, пересекающаяся с сохранённой, не добавляется.
    @Test
    void overlappingBookingIsRejected() throws Exception {
        Path databasePath = temporaryDirectory.resolve("overlap.db");
        Booking first = booking(10L, 101L, "2030-04-12T18:00", 2);
        Booking second = booking(10L, 102L, "2030-04-12T19:00", 1);

        try (SqliteBookingRepository repository = new SqliteBookingRepository(databasePath)) {
            repository.refreshInitialBookings(List.of());

            assertTrue(repository.addIfFree(first));
            assertFalse(repository.addIfFree(second));
            assertEquals(List.of(first), repository.findByUser(101L));
            assertTrue(repository.findByUser(102L).isEmpty());
        }
    }

    /// Проверяет, что демонстрационные записи обновляются при запуске, а брони клиентов сохраняются.
    @Test
    void initialBookingsRefreshWithoutDeletingCustomerBookings() throws Exception {
        Path databasePath = temporaryDirectory.resolve("initial.db");
        Booking firstInitial = booking(10L, 0L, "2030-04-12T18:00", 2);
        Booking userBooking = booking(10L, 101L, "2030-04-13T18:00", 2);
        Booking refreshedInitial = booking(11L, 0L, "2030-04-14T13:00", 3);

        try (SqliteBookingRepository repository = new SqliteBookingRepository(databasePath)) {
            repository.refreshInitialBookings(List.of(firstInitial));
            assertTrue(repository.addIfFree(userBooking));
        }

        try (SqliteBookingRepository repository = new SqliteBookingRepository(databasePath)) {
            repository.refreshInitialBookings(List.of(refreshedInitial));

            assertEquals(List.of(refreshedInitial), repository.findByUser(0L));
            assertEquals(List.of(userBooking), repository.findByUser(101L));
        }
    }

    /// Проверяет, что начальная запись не пересекается с записью клиента и в таком случае пропускается.
    @Test
    void initialBookingIsSkippedWhenCustomerAlreadyReservedItsSlot() throws Exception {
        Path databasePath = temporaryDirectory.resolve("initial-conflict.db");
        Booking userBooking = booking(10L, 101L, "2030-04-13T18:00", 2);
        Booking conflictingInitial = booking(10L, 0L, "2030-04-13T19:00", 2);

        try (SqliteBookingRepository repository = new SqliteBookingRepository(databasePath)) {
            repository.refreshInitialBookings(List.of());
            assertTrue(repository.addIfFree(userBooking));
            repository.refreshInitialBookings(List.of(conflictingInitial));

            assertEquals(List.of(userBooking), repository.findByUser(101L));
            assertTrue(repository.findByUser(0L).isEmpty());
        }
    }

    /// Проверяет добавление колонки демонстрационных записей к старой схеме SQLite.
    @Test
    void oldDatabaseSchemaIsMigratedAndCustomerBookingsArePreserved() throws Exception {
        Path databasePath = temporaryDirectory.resolve("old-schema.db");
        Booking oldCustomerBooking = booking(12L, 101L, "2030-04-15T18:00", 2);
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath());
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE bookings (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "room_id INTEGER NOT NULL, user_id INTEGER NOT NULL, starts_at TEXT NOT NULL, "
                    + "hours INTEGER NOT NULL, customer_name TEXT NOT NULL, phone TEXT NOT NULL, "
                    + "comment TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.execute(
                    "INSERT INTO bookings (room_id, user_id, starts_at, hours, customer_name, phone, comment) "
                            + "VALUES (12, 101, '2030-04-15T18:00', 2, 'Тестовый клиент', '+79991234567', "
                            + "'Тестовая запись')");
            statement.execute(
                    "INSERT INTO bookings (room_id, user_id, starts_at, hours, customer_name, phone, comment) "
                            + "VALUES (10, 0, '2030-04-12T18:00', 2, 'Старая демо-запись', '+70000000000', '')");
        }

        Booking refreshedInitial = booking(13L, 0L, "2030-04-16T13:00", 3);
        try (SqliteBookingRepository repository = new SqliteBookingRepository(databasePath)) {
            repository.refreshInitialBookings(List.of(refreshedInitial));
            assertEquals(List.of(oldCustomerBooking), repository.findByUser(101L));
            assertEquals(List.of(refreshedInitial), repository.findByUser(0L));
        }
    }

    /// Проверяет, что при одновременном бронировании одного слота SQLite сохраняет только одну заявку.
    @Test
    void onlyOneOfTwoConcurrentSqliteBookingsSucceeds() throws Exception {
        Path databasePath = temporaryDirectory.resolve("concurrent.db");
        LocalDateTime start = LocalDateTime.parse("2030-04-12T18:00");
        try (SqliteBookingRepository repository = new SqliteBookingRepository(databasePath)) {
            repository.refreshInitialBookings(List.of());
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> first = executor.submit(() -> tryConcurrentBooking(
                        repository, ready, go, booking(10L, 101L, start.toString(), 2)));
                Future<Boolean> second = executor.submit(() -> tryConcurrentBooking(
                        repository, ready, go, booking(10L, 102L, start.toString(), 2)));
                ready.await();
                go.countDown();

                assertNotEquals(first.get(), second.get());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    /// Ожидает общий сигнал и пытается добавить бронь в SQLite.
    ///
    /// @param repository хранилище бронирований
    /// @param ready счётчик готовых потоков
    /// @param go сигнал одновременного старта
    /// @param booking проверяемая бронь
    /// @return true, если бронь создана
    /// @throws InterruptedException если поток был прерван
    private boolean tryConcurrentBooking(SqliteBookingRepository repository, CountDownLatch ready,
                                         CountDownLatch go, Booking booking) throws InterruptedException {
        ready.countDown();
        go.await();
        return repository.addIfFree(booking);
    }

    /// Создаёт тестовую бронь с нормализованным телефоном и комментарием.
    ///
    /// @param roomId идентификатор зала
    /// @param userId идентификатор клиента
    /// @param start дата и время начала в формате ISO
    /// @param hours длительность в часах
    /// @return тестовая бронь
    private Booking booking(long roomId, long userId, String start, int hours) {
        return new Booking(roomId, userId, LocalDateTime.parse(start), hours, "Тестовый клиент",
                "+79991234567", "Тестовая запись");
    }
}
