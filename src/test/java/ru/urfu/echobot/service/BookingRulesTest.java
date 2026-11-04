package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.TestStudioData;
import ru.urfu.echobot.model.Base;

/// Tests booking rules against a SQLite fixture and a fixed Moscow-local clock.
final class BookingRulesTest {
    /// Фиксированные часы: 07:00 UTC, то есть 10:00 в Москве.
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
    /// Проверяемые правила.
    private BookingRules rules;
    /// База «Центр»: Пн-Сб 9:00-24:00, бронь не позднее чем за 2 часа.
    private Base base;

    /// Isolated temporary directory for the SQLite fixture.
    @TempDir
    private Path temporaryDirectory;

    /// Given: будний день с запасом по времени. When: проверяем бронь. Then: можно.
    @Test
    void bookingInsideWorkingHoursIsAllowed() {
        createFixture();
        assertTrue(rules.canBook(base, LocalDateTime.of(2026, 10, 24, 18, 0), 2));
    }

    /// Given: начало ближе допустимого срока. When: проверяем. Then: нельзя, а ровно через 2 часа можно.
    @Test
    void bookingCloserThanLeadTimeIsRejected() {
        createFixture();
        assertFalse(rules.canBook(base, LocalDateTime.of(2026, 10, 20, 11, 0), 1));
        assertTrue(rules.canBook(base, LocalDateTime.of(2026, 10, 20, 12, 0), 1));
    }

    /// Given: бронь выходит за закрытие. When: проверяем. Then: нельзя, а до самого закрытия можно.
    @Test
    void bookingAfterClosingIsRejected() {
        createFixture();
        assertFalse(rules.canBook(base, LocalDateTime.of(2026, 10, 24, 23, 0), 2));
        assertTrue(rules.canBook(base, LocalDateTime.of(2026, 10, 24, 22, 0), 2));
    }

    /// Given: бронь до открытия. When: проверяем. Then: нельзя.
    @Test
    void bookingBeforeOpeningIsRejected() {
        createFixture();
        assertFalse(rules.canBook(base, LocalDateTime.of(2026, 10, 24, 8, 0), 1));
    }

    /// Given: воскресенье, когда база закрыта. When: проверяем. Then: нельзя.
    @Test
    void bookingOnDayOffIsRejected() {
        createFixture();
        assertFalse(rules.canBook(base, LocalDateTime.of(2026, 10, 25, 12, 0), 1));
    }

    /// Given: фиксированное время. When: спрашиваем сегодняшнюю дату базы. Then: 20 октября.
    @Test
    void todayIsTakenFromClockInBaseTimezone() {
        createFixture();
        assertEquals(LocalDate.of(2026, 10, 20), rules.today(base));
    }

    /// Builds the SQLite test fixture and initializes the tested rules and base.
    private void createFixture() {
        TestStudioData data = new TestStudioData(temporaryDirectory, clock);
        rules = new BookingRules(clock);
        base = data.center();
    }
}
