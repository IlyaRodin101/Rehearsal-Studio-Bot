package ru.urfu.echobot.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.urfu.echobot.model.Booking;

/// Тесты хранилища броней в памяти.
final class InMemoryBookingRepositoryTest {
    /// Начало тестовой брони.
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 24, 18, 0);
    /// Проверяемое хранилище.
    private InMemoryBookingRepository repository;

    /// Создаёт пустое хранилище перед каждым тестом.
    @BeforeEach
    void setUp() {
        repository = new InMemoryBookingRepository(List.of());
    }

    /// Создаёт бронь на заданный зал, клиента, время и длительность.
    ///
    /// @param roomId зал
    /// @param userId клиент
    /// @param start  начало
    /// @param hours  длительность в часах
    /// @return бронь
    private Booking booking(long roomId, long userId, LocalDateTime start, int hours) {
        return new Booking(roomId, userId, start, hours, "Клиент", "+79990000000", "");
    }

    /// Given: свободный зал. When: добавляем бронь. Then: бронь добавлена.
    @Test
    void freeRoomAcceptsBooking() {
        assertTrue(repository.addIfFree(booking(1, 10, START, 2)));
    }

    /// Given: зал занят с 18 до 20. When: добавляем пересекающуюся бронь. Then: отказ.
    @Test
    void overlappingBookingIsRejected() {
        repository.addIfFree(booking(1, 10, START, 2));

        assertFalse(repository.addIfFree(booking(1, 11, START.plusHours(1), 2)));
        assertFalse(repository.addIfFree(booking(1, 11, START.minusHours(1), 2)));
        assertFalse(repository.addIfFree(booking(1, 11, START, 1)));
    }

    /// Given: зал занят с 18 до 20. When: бронируем сразу после или в другом зале. Then: можно.
    @Test
    void adjacentBookingAndOtherRoomAreAllowed() {
        repository.addIfFree(booking(1, 10, START, 2));

        assertTrue(repository.addIfFree(booking(1, 11, START.plusHours(2), 1)));
        assertTrue(repository.addIfFree(booking(2, 11, START, 2)));
    }

    /// Given: брони двух клиентов. When: ищем по клиенту. Then: только его брони по возрастанию времени.
    @Test
    void bookingsOfUserAreSortedByStart() {
        repository.addIfFree(booking(1, 10, START.plusHours(3), 1));
        repository.addIfFree(booking(1, 10, START, 1));
        repository.addIfFree(booking(1, 11, START.plusHours(5), 1));

        List<Booking> found = repository.findByUser(10);

        assertEquals(2, found.size());
        assertEquals(START, found.get(0).start());
    }

    /// Given: два клиента одновременно бронируют одно время. When: обе попытки идут параллельно.
    /// Then: успешна ровно одна.
    ///
    /// @throws Exception если поток прервали
    @Test
    void onlyOneOfTwoConcurrentBookingsSucceeds() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> tryBook(ready, go, 10));
            Future<Boolean> second = executor.submit(() -> tryBook(ready, go, 11));
            ready.await();
            go.countDown();

            assertNotEquals(first.get(), second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    /// Дожидается общего сигнала и пытается создать бронь.
    ///
    /// @param ready счётчик готовых потоков
    /// @param go    сигнал одновременного старта
    /// @param userId клиент
    /// @return true, если бронь создана
    /// @throws InterruptedException если поток прервали
    private Boolean tryBook(CountDownLatch ready, CountDownLatch go, long userId) throws InterruptedException {
        ready.countDown();
        go.await();
        return repository.addIfFree(booking(1, userId, START, 2));
    }
}
