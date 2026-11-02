package ru.urfu.echobot.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.urfu.echobot.model.Booking;

/// Тесты начальных данных, которые создаются при запуске бота.
final class InitialDataTest {

    /// Создаёт часы, зафиксированные на полдень указанного дня по Москве.
    ///
    /// @param isoDate дата в формате ГГГГ-ММ-ДД
    /// @return фиксированные часы
    private Clock clockAt(String isoDate) {
        return Clock.fixed(Instant.parse(isoDate + "T09:00:00Z"), ZoneOffset.UTC);
    }

    /// Given: любой запуск. When: берём данные. Then: две базы, четыре зала, две записи.
    @Test
    void containsTwoBasesFourRoomsAndTwoBookings() {
        InitialData data = new InitialData(clockAt("2026-10-20"));

        assertEquals(2, data.bases().size());
        assertEquals(4, data.rooms().size());
        assertEquals(2, data.bookings().size());
    }

    /// Given: запуск во вторник. When: берём записи. Then: они завтра и послезавтра.
    @Test
    void bookingsAreTomorrowAndDayAfterTomorrow() {
        List<Booking> bookings = new InitialData(clockAt("2026-10-20")).bookings();

        assertEquals(LocalDate.of(2026, 10, 21), bookings.get(0).start().toLocalDate());
        assertEquals(LocalDate.of(2026, 10, 22), bookings.get(1).start().toLocalDate());
    }

    /// Given: запуск в субботу, когда «завтра» воскресенье. When: берём записи.
    /// Then: ни одна запись не приходится на выходной.
    @Test
    void bookingsAreNeverOnSunday() {
        List<Booking> bookings = new InitialData(clockAt("2026-10-24")).bookings();

        assertTrue(bookings.stream().noneMatch(b -> b.start().getDayOfWeek() == DayOfWeek.SUNDAY));
    }
}
