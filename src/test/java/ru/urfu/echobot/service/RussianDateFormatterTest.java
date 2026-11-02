package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// Тесты форматирования дат и времени.
final class RussianDateFormatterTest {
    private static final int TEST_YEAR = 2026;
    private static final int OCTOBER = 10;
    private static final int DAY_19 = 19;
    private static final int DAY_24 = 24;
    private static final int DAY_25 = 25;
    private static final int HOUR_9 = 9;
    private static final int HOUR_18 = 18;
    private static final int HOUR_22 = 22;

    /// Проверяемый форматер.
    private final RussianDateFormatter formatter = new RussianDateFormatter();

    /// Given: час 9. When: форматируем. Then: «9:00».
    @Test
    void hourHasZeroMinutes() {
        Assertions.assertEquals("9:00", formatter.hour(HOUR_9));
    }

    /// Given: бронь с 22:00 до полуночи. When: форматируем конец. Then: «24:00».
    @Test
    void midnightEndIsShownAsTwentyFour() {
        LocalDateTime start = LocalDateTime.of(TEST_YEAR, OCTOBER, DAY_24, HOUR_22, 0);

        Assertions.assertEquals("24:00", formatter.endTime(start, start.plusHours(2)));
    }

    /// Given: бронь в пределах дня. When: форматируем конец. Then: обычный час.
    @Test
    void ordinaryEndIsShownAsHour() {
        LocalDateTime start = LocalDateTime.of(TEST_YEAR, OCTOBER, DAY_24, HOUR_18, 0);

        Assertions.assertEquals("20:00", formatter.endTime(start, start.plusHours(2)));
    }

    /// Given: октябрь 2026. When: форматируем. Then: заглавная буква и год.
    @Test
    void monthYearStartsWithCapitalLetter() {
        String text = formatter.monthYear(YearMonth.of(TEST_YEAR, OCTOBER));

        Assertions.assertTrue(Character.isUpperCase(text.charAt(0)));
        Assertions.assertTrue(text.endsWith("2026"));
    }

    /// Given: понедельник и воскресенье. When: берём названия. Then: «Пн» и «Вс».
    @Test
    void dayNamesAreInRussian() {
        Assertions.assertEquals("Пн", formatter.dayName(DayOfWeek.MONDAY));
        Assertions.assertEquals("Вс", formatter.dayName(DayOfWeek.SUNDAY));
    }

    /// Проверяет форматирование даты без года.
    @Test
    void shortDateHasExpectedFormat() {
        LocalDate date = LocalDate.of(TEST_YEAR, OCTOBER, DAY_24);

        Assertions.assertEquals("24 окт.", formatter.shortDate(date));
    }

    /// Проверяет форматирование даты и времени.
    @Test
    void dateTimeHasExpectedFormat() {
        LocalDateTime dateTime =
                LocalDateTime.of(TEST_YEAR, OCTOBER, DAY_24, HOUR_18, 0);

        Assertions.assertEquals(
                "сб 24 окт., 18:00",
                formatter.dateTime(dateTime)
        );
    }

    /// Проверяет формат даты для кнопки выбора дня.
    @Test
    void dayAndDateHasExpectedFormat() {
        LocalDate date = LocalDate.of(TEST_YEAR, OCTOBER, DAY_24);

        Assertions.assertEquals("сб, 24 окт.", formatter.dayAndDate(date));
    }

    /// Проверяет точное название месяца и год.
    @Test
    void monthYearHasExpectedFormat() {
        Assertions.assertEquals(
                "Октябрь 2026",
                formatter.monthYear(YearMonth.of(TEST_YEAR, OCTOBER))
        );
    }

    /// Проверяет форматирование диапазона внутри одного месяца.
    @Test
    void weekRangeHasExpectedFormat() {
        LocalDate start = LocalDate.of(TEST_YEAR, OCTOBER, DAY_19);
        LocalDate end = LocalDate.of(TEST_YEAR, OCTOBER, DAY_25);

        Assertions.assertEquals("19-25 окт.", formatter.weekRange(start, end));
    }

    /// Проверяет отклонение диапазона с неправильным порядком дат.
    @Test
    void weekRangeRejectsEndBeforeStart() {
        LocalDate start = LocalDate.of(TEST_YEAR, OCTOBER, DAY_25);
        LocalDate end = LocalDate.of(TEST_YEAR, OCTOBER, DAY_19);

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> formatter.weekRange(start, end)
        );
    }
}
