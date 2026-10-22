package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/// Форматирует даты, время и названия дней недели для сообщений бота на русском языке.
public final class RussianDateFormatter {
    /// Русская локаль для названий месяцев и дней.
    private final Locale russian = Locale.of("ru", "RU");
    /// Формат даты, например «24 окт.».
    private final DateTimeFormatter shortDateFormat = DateTimeFormatter.ofPattern("d MMM", russian);
    /// Формат месяца с годом, например «октябрь 2026».
    private final DateTimeFormatter monthYearFormat = DateTimeFormatter.ofPattern("LLLL yyyy", russian);
    /// Формат даты со временем, например «сб 24 окт., 18:00».
    private final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("EEE d MMM, H:mm", russian);
    /// Сокращённые названия дней недели, начиная с понедельника.
    private final List<String> dayNames = List.of("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс");

    /// Форматирует час.
    ///
    /// @param hour час от 0 до 24
    /// @return строка вида «9:00»
    public String hour(int hour) {
        return hour + ":00";
    }

    /// Форматирует время окончания брони. Полночь следующего дня выводится как 24:00.
    ///
    /// @param start начало брони
    /// @param end   окончание брони
    /// @return строка вида «20:00» или «24:00»
    public String endTime(@NotNull LocalDateTime start, @NotNull LocalDateTime end) {
        if (end.toLocalTime().equals(LocalTime.MIDNIGHT) && end.toLocalDate().isAfter(start.toLocalDate())) {
            return hour(24);
        }
        return hour(end.getHour());
    }

    /// Форматирует дату без года.
    ///
    /// @param date дата
    /// @return строка вида «24 окт.»
    public String shortDate(@NotNull LocalDate date) {
        return date.format(shortDateFormat);
    }

    /// Форматирует диапазон дат для кнопки выбора недели.
    ///
    /// @param start первый день диапазона
    /// @param end последний день диапазона
    /// @return строка вида «19-25 окт.»
    /// @throws IllegalArgumentException если конец раньше начала
    public String weekRange(@NotNull LocalDate start, @NotNull LocalDate end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException(
                    "Конец диапазона не может быть раньше начала");
        }

        return start.getDayOfMonth() + "-" + shortDate(end);
    }

    /// Форматирует месяц с годом, первая буква заглавная.
    ///
    /// @param month месяц
    /// @return строка вида «Октябрь 2026»
    public String monthYear(@NotNull YearMonth month) {
        String text = month.format(monthYearFormat);
        return text.substring(0, 1).toUpperCase(russian) + text.substring(1);
    }

    /// Форматирует начало брони: день недели, дата и время.
    ///
    /// @param dateTime дата и время
    /// @return строка вида «сб 24 окт., 18:00»
    public String dateTime(@NotNull LocalDateTime dateTime) {
        return dateTime.format(dateTimeFormat);
    }

    /// Форматирует день для кнопки: сокращённый день недели и дата.
    ///
    /// @param date дата
    /// @return строка вида «сб, 24 окт.»
    public String dayAndDate(@NotNull LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, russian) + ", " + shortDate(date);
    }

    /// Возвращает сокращённое название дня недели.
    ///
    /// @param day день недели
    /// @return «Пн», «Вт» и так далее
    public String dayName(@NotNull DayOfWeek day) {
        return dayNames.get(day.getValue() - 1);
    }
}
