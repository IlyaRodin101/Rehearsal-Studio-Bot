package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Тесты хранилища: конкурентные бронирования и сохранность данных после перезапуска.
final class DatabaseTest {
    /// Временная папка, в которой создаётся файл базы данных.
    @TempDir
    private Path tempDir;

    /// Given: две одновременные попытки забронировать одно время.
    /// When: обе выполняются параллельно.
    /// Then: успешна ровно одна, и запись сохраняется после перезапуска базы.
    ///
    /// @throws Exception если тест не смог выполниться
    @Test
    void competingBookingsCannotOverlapAndPersistAfterRestart() throws Exception {
        Path databaseFile = tempDir.resolve("booking.db");
        Database database = new Database(databaseFile.toString());
        database.initialize();

        int[][] schedule = new int[7][];
        for (int day = 0; day < schedule.length; day++) {
            schedule[day] = new int[]{0, 24};
        }
        List<String> rooms = new ArrayList<String>();
        rooms.add("Зал 1");
        database.createBase("Тестовая база", "Адрес", "UTC", 2, "Микрофоны",
                schedule, rooms, new boolean[]{false});
        long roomId = database.getRooms(database.getBases().get(0).id).get(0).id;
        LocalDateTime tooSoon = LocalDateTime.now(ZoneId.of("UTC")).plusHours(1)
                .withMinute(0).withSecond(0).withNano(0);
        assertFalse(database.createBooking(roomId, 4, tooSoon, 1,
                "Клиент", "+79990000000", ""));
        LocalDateTime tooLate = LocalDateTime.now(ZoneId.of("UTC")).plusDays(2)
                .withHour(23).withMinute(0).withSecond(0).withNano(0);
        assertFalse(database.createBooking(roomId, 5, tooLate, 2,
                "Клиент", "+79990000000", ""));
        LocalDateTime start = LocalDateTime.now(ZoneId.of("UTC")).plusDays(2)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> tryCreate(database, roomId, start, ready, startTogether, 1));
            Future<Boolean> second = executor.submit(() -> tryCreate(database, roomId, start, ready, startTogether, 2));
            ready.await();
            startTogether.countDown();
            boolean firstCreated = first.get();
            boolean secondCreated = second.get();
            assertNotEquals(firstCreated, secondCreated);

            Database restarted = new Database(databaseFile.toString());
            restarted.initialize();
            assertEquals(1, restarted.getUpcomingBookings(firstCreated ? 1 : 2).size()
                    + restarted.getUpcomingBookings(firstCreated ? 2 : 1).size());
            assertFalse(restarted.createBooking(roomId, 3, start.plusHours(1), 1,
                    "Клиент", "+79990000000", ""));
            assertTrue(restarted.createBooking(roomId, 3, start.plusHours(2), 1,
                    "Клиент", "+79990000000", ""));
        } finally {
            executor.shutdownNow();
        }
    }

    /// Пытается создать бронь, дождавшись общего сигнала старта для всех потоков.
    ///
    /// @param database      проверяемая база данных
    /// @param roomId        идентификатор зала
    /// @param start         время начала записи
    /// @param ready         счётчик готовых потоков
    /// @param startTogether сигнал одновременного старта
    /// @param userId        идентификатор пользователя
    /// @return true, если бронь создана
    /// @throws Exception если поток прервали или произошла ошибка базы
    private Boolean tryCreate(Database database, long roomId, LocalDateTime start,
                              CountDownLatch ready, CountDownLatch startTogether, long userId) throws Exception {
        ready.countDown();
        startTogether.await();
        return database.createBooking(roomId, userId, start, 2,
                "Клиент " + userId, "+79990000000", "");
    }
}