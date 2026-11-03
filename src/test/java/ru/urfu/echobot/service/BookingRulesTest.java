package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.repository.InitialData;

/// Тесты правил бронирования. Текущее время зафиксировано: вторник, 20 октября 2026, 10:00 по Москве.
final class BookingRulesTest {
    /// Фиксированные часы: 07:00 UTC, то есть 10:00 в Москве.
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
    /// Проверяемые правила.
    private BookingRules rules;
    /// База «Центр»: Пн-Сб 9:00-24:00, бронь не позднее чем за 2 часа.
    private Base base;

    /// Создаёт правила и берёт первую начальную базу.
    @BeforeEach
    void setUp() {
        rules = new BookingRules(clock);
        base = new InitialData(clock).bases().get(0);
    }

    /// Given: будний день с запасом по времени. When: проверяем бронь. Then: можно.
    @Test
    void bookingInsideWorkingHoursIsAllowed() {
        assertTrue(rules.canBook(base, LocalDateTime.of(2026, 10, 24, 18, 0), 2));
    }

    /// Given: начало ближе допустимого срока. When: проверяем. Then: нельзя, а ровно через 2 часа можно.
    @Test
    void bookingCloserThanLeadTimeIsRejected() {
        assertFalse(rules.canBook(base, LocalDateTime.of(2026, 10, 20, 11, 0), 1));
        assertTrue(rules.canBook(base, LocalDateTime.of(2026, 10, 20, 12, 0), 1));
    }

    /// Given: бронь выходит за закрытие. When: проверяем. Then: нельзя, а до самого закрытия можно.
    @Test
    void bookingAfterClosingIsRejected() {
        assertFalse(rules.canBook(base, LocalDateTime.of(2026, 10, 24, 23, 0), 2));
        assertTrue(rules.canBook(base, LocalDateTime.of(2026, 10, 24, 22, 0), 2));
    }

    /// Given: бронь до открытия. When: проверяем. Then: нельзя.
    @Test
    void bookingBeforeOpeningIsRejected() {
        assertFalse(rules.canBook(base, LocalDateTime.of(2026, 10, 24, 8, 0), 1));
    }

    /// Given: воскресенье, когда база закрыта. When: проверяем. Then: нельзя.
    @Test
    void bookingOnDayOffIsRejected() {
        assertFalse(rules.canBook(base, LocalDateTime.of(2026, 10, 25, 12, 0), 1));
    }

    /// Given: фиксированное время. When: спрашиваем сегодняшнюю дату базы. Then: 20 октября.
    @Test
    void todayIsTakenFromClockInBaseTimezone() {
        assertEquals(LocalDate.of(2026, 10, 20), rules.today(base));
    }
}
