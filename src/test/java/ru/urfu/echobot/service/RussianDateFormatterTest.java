package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Тесты форматирования дат и времени.
final class RussianDateFormatterTest {
    /// Проверяемый форматер.
    private final RussianDateFormatter formatter = new RussianDateFormatter();

    /// Given: час 9. When: форматируем. Then: «9:00».
    @Test
    void hourHasZeroMinutes() {
        assertEquals("9:00", formatter.hour(9));
    }

    /// Given: бронь с 22:00 до полуночи. When: форматируем конец. Then: «24:00».
    @Test
    void midnightEndIsShownAsTwentyFour() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 24, 22, 0);

        assertEquals("24:00", formatter.endTime(start, start.plusHours(2)));
    }

    /// Given: бронь в пределах дня. When: форматируем конец. Then: обычный час.
    @Test
    void ordinaryEndIsShownAsHour() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 24, 18, 0);

        assertEquals("20:00", formatter.endTime(start, start.plusHours(2)));
    }

    /// Given: октябрь 2026. When: форматируем. Then: заглавная буква и год.
    @Test
    void monthYearStartsWithCapitalLetter() {
        String text = formatter.monthYear(YearMonth.of(2026, 10));

        assertTrue(Character.isUpperCase(text.charAt(0)));
        assertTrue(text.endsWith("2026"));
    }

    /// Given: понедельник и воскресенье. When: берём названия. Then: «Пн» и «Вс».
    @Test
    void dayNamesAreInRussian() {
        assertEquals("Пн", formatter.dayName(DayOfWeek.MONDAY));
        assertEquals("Вс", formatter.dayName(DayOfWeek.SUNDAY));
    }

    /// Проверяет форматирование даты без года.
    @Test
    void shortDateHasExpectedFormat() {
        LocalDate date = LocalDate.of(2026, 10, 24);

        assertEquals("24 окт.", formatter.shortDate(date));
    }

    /// Проверяет форматирование даты и времени.
    @Test
    void dateTimeHasExpectedFormat() {
        LocalDateTime dateTime =
                LocalDateTime.of(2026, 10, 24, 18, 0);

        assertEquals(
                "сб 24 окт., 18:00",
                formatter.dateTime(dateTime)
        );
    }

    /// Проверяет формат даты для кнопки выбора дня.
    @Test
    void dayAndDateHasExpectedFormat() {
        LocalDate date = LocalDate.of(2026, 10, 24);

        assertEquals("сб, 24 окт.", formatter.dayAndDate(date));
    }

    /// Проверяет точное название месяца и год.
    @Test
    void monthYearHasExpectedFormat() {
        assertEquals(
                "Октябрь 2026",
                formatter.monthYear(YearMonth.of(2026, 10))
        );
    }

    /// Проверяет форматирование диапазона внутри одного месяца.
    @Test
    void weekRangeHasExpectedFormat() {
        LocalDate start = LocalDate.of(2026, 10, 19);
        LocalDate end = LocalDate.of(2026, 10, 25);

        assertEquals("19-25 окт.", formatter.weekRange(start, end));
    }

    /// Проверяет отклонение диапазона с неправильным порядком дат.
    @Test
    void weekRangeRejectsEndBeforeStart() {
        LocalDate start = LocalDate.of(2026, 10, 25);
        LocalDate end = LocalDate.of(2026, 10, 19);

        assertThrows(
                IllegalArgumentException.class,
                () -> formatter.weekRange(start, end)
        );
    }
}
