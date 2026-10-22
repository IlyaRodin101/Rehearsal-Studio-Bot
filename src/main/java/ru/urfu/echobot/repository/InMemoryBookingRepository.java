package ru.urfu.echobot.repository;

import ru.urfu.echobot.model.Booking;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// Хранилище броней в памяти. Брони живут, пока работает бот.
/// Все методы синхронизированы: бот обслуживает много пользователей одновременно.
public final class InMemoryBookingRepository implements BookingRepository {
    /// Все брони в порядке добавления.
    private final List<Booking> bookings = new ArrayList<>();

    /// Создаёт хранилище с начальным набором броней.
    ///
    /// @param initialBookings брони, которые уже есть на момент запуска
    public InMemoryBookingRepository(List<Booking> initialBookings) {
        bookings.addAll(initialBookings);
    }

    @Override
    public synchronized boolean addIfFree(Booking booking) {
        if (hasConflict(booking.roomId(), booking.start(), booking.end())) {
            return false;
        }
        bookings.add(booking);
        return true;
    }

    @Override
    public synchronized boolean hasConflict(long roomId, LocalDateTime start, LocalDateTime end) {
        return bookings.stream().anyMatch(booking -> booking.roomId() == roomId
                && booking.start().isBefore(end)
                && booking.end().isAfter(start));
    }

    @Override
    public synchronized List<Booking> findByUser(long userId) {
        return bookings.stream()
                .filter(booking -> booking.userId() == userId)
                .sorted(Comparator.comparing(Booking::start))
                .toList();
    }
}
